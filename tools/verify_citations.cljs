#!/usr/bin/env nbb
;; Live gate for facts/catalog.edn (organisation-record citations).
;;
;; Each DISTINCT :cite/url is fetched exactly once, and every row that names
;; that url is checked against that one body:
;;   * require HTTP 2xx
;;   * if :cite/expect-substring is non-empty, require it in the body
;;
;; Fetching once per url rather than once per row is not only cheaper, it is
;; required. kbopub.economie.fgov.be answers a burst of repeated lookups with a
;; 302 to captchaform.html, whose page states that consultation of Public
;; Search "may only take place enterprise by enterprise" under article 2 of the
;; Royal Decree of 28 March 2014. A catalog with eighteen rows on two KBO pages
;; used to issue eighteen GETs for two documents. It now issues two.
;;
;; Exit codes:
;;   0 — answered, every citation checked, floor met
;;   1 — answered, at least one citation wrong
;;   2 — could not answer (parse failure, network, zero checks, floor miss,
;;       or a register that served a challenge instead of a record)
;;
;; "Nothing was checked" and "nothing was wrong" must not share an exit code.
;; Neither may "the register refused to answer me" and "the register no longer
;; carries this claim": a challenge page is reported as BLOCKED and exits 2, it
;; is never reported as DRIFT and it is never worked around. If a register
;; challenges this gate, the gate stops and says so.

(ns verify-citations
  (:require [clojure.edn :as edn]
            [clojure.string :as str]
            ["fs" :as fs]
            ["https" :as https]
            ["http" :as http]
            ["url" :as url]))

(def argv (vec (drop 3 (js->clj js/process.argv))))
(defn flag? [f] (boolean (some #{f} argv)))
(defn flag-val [f default]
  (let [i (.indexOf argv f)]
    (if (neg? i) default (get argv (inc i) default))))

(def quiet? (flag? "--quiet"))
(def min-citations (js/parseInt (flag-val "--min" "8") 10))
(def gap-ms (js/parseInt (flag-val "--gap-ms" "200") 10))
;; Strip flag *values* as well as flags so `--min 13` cannot become the catalog path
;; (fleet gates put <dir> first; locally people put flags first — both must work).
(def catalog-path
  (let [skip-next? (atom false)
        positional
        (reduce (fn [acc a]
                  (cond
                    @skip-next? (do (reset! skip-next? false) acc)
                    (#{"--min" "--gap-ms"} a) (do (reset! skip-next? true) acc)
                    (str/starts-with? a "--") acc
                    :else (conj acc a)))
                []
                argv)]
    (or (first positional) "facts/catalog.edn")))

(defn say [& xs] (when-not quiet? (println (str/join " " xs))))

(defn sleep [ms]
  (js/Promise. (fn [resolve _] (js/setTimeout resolve ms))))

;; A register that answers with an interstitial challenge has not answered.
;; These markers are deliberately narrow and are matched against the redirect
;; chain and the delivered body. Nothing here attempts to satisfy, solve or
;; evade a challenge — the only response to one is to stop and report it.
(def challenge-location-markers ["captcha" "challenge"])
(def challenge-body-markers ["CAPTCHA Test" "Test CAPTCHA" "CAPTCHA-Test"])

(defn challenged? [{:keys [chain body]}]
  (let [loc-hit (some (fn [[_ loc]]
                        (some #(str/includes? (str/lower-case (str loc)) %)
                              challenge-location-markers))
                      chain)
        body-hit (some #(str/includes? (or body "") %) challenge-body-markers)]
    (boolean (or loc-hit body-hit))))

(defn fetch-url
  ([u] (fetch-url u 0 []))
  ([u hops chain]
   (js/Promise.
    (fn [resolve reject]
      (try
        (let [parsed (url/parse u)
              lib (if (= (.-protocol parsed) "http:") http https)
              req (.request
                   lib
                   #js {:protocol (.-protocol parsed)
                        :hostname (.-hostname parsed)
                        :port (.-port parsed)
                        :path (.-path parsed)
                        :method "GET"
                        :headers #js {"User-Agent" "lei-record-maturity-gate/1.0"
                                      "Accept" "*/*"}
                        :timeout 30000}
                   (fn [res]
                     (let [status (.-statusCode res)
                           loc (and (.-headers res) (aget (.-headers res) "location"))]
                       (if (and loc (<= 300 status 399) (< hops 8))
                         (do (.resume res)
                             (-> (fetch-url (url/resolve u loc) (inc hops)
                                            (conj chain [status loc]))
                                 (.then resolve)
                                 (.catch reject)))
                         (let [chunks #js []]
                           (.on res "data" (fn [c] (.push chunks c)))
                           (.on res "end"
                                (fn []
                                  (resolve {:status status
                                            :chain chain
                                            :body (.toString (js/Buffer.concat chunks)
                                                             "utf8")})))
                           (.on res "error" reject))))))]
          (.on req "error" reject)
          (.on req "timeout" (fn [] (.destroy req) (reject (js/Error. "timeout"))))
          (.end req))
        (catch :default e (reject e)))))))

(defn finish! [code]
  (set! (.-exitCode js/process) code)
  ;; Give pending console I/O a tick, then exit hard so nbb cannot report 0
  ;; after an async failure (measured: process.exit from a .then raced the
  ;; runtime teardown and the shell saw 0 while DRIFT lines had already printed).
  (js/setTimeout (fn [] (js/process.exit code)) 50))

(defn check-entry
  "Pure: judge one row against the already-fetched result for its url."
  [e fetched]
  (let [{:keys [status body error]} fetched
        expect (or (:cite/expect-substring e) "")]
    (cond
      error {:ok? false :id (:cite/id e) :why (str "fetch-error " error)}
      (not (<= 200 status 299)) {:ok? false :id (:cite/id e) :why (str "HTTP " status)}
      (and (not (str/blank? expect)) (not (str/includes? (or body "") expect)))
      {:ok? false :id (:cite/id e) :why (str "missing substring " (pr-str expect))}
      :else {:ok? true :id (:cite/id e) :status status})))

(defn read-catalog
  "Returns [:ok entries] or [:cannot-answer reason]. One condition, one line —
   \"missing\", \"unparseable\" and \"empty\" are three different facts and must not
   all be printed for one of them."
  []
  (if-not (.existsSync fs catalog-path)
    [:cannot-answer (str "MISSING catalog " catalog-path)]
    (let [parsed (try {:v (edn/read-string (.readFileSync fs catalog-path "utf8"))}
                      (catch :default e {:err (.-message e)}))]
      (cond
        (:err parsed) [:cannot-answer (str "PARSE-FAIL " (:err parsed))]
        (not (seq (:catalog/entries (:v parsed)))) [:cannot-answer "EMPTY catalog entries"]
        :else [:ok (:catalog/entries (:v parsed))]))))

(defn judge!
  "All rows judged against the already-fetched bodies. Never called if any url
   was challenged."
  [entries fetched]
  (let [results (mapv (fn [e] (check-entry e (get fetched (:cite/url e)))) entries)
        n (count results)
        bad (filterv (complement :ok?) results)]
    (doseq [r results] (say (if (:ok? r) "OK" "FAIL") (:id r) (or (:why r) (:status r))))
    (say "CHECKED" n "OK" (- n (count bad)) "FAIL" (count bad) "MIN" min-citations)
    (cond
      (zero? n)              (do (println "FLOOR zero checks") (finish! 2))
      (< n min-citations)    (do (println "FLOOR below --min" min-citations "got" n) (finish! 2))
      (seq bad)              (do (doseq [b bad] (println "DRIFT" (:id b) (:why b))) (finish! 1))
      :else                  (do (say "PASS") (finish! 0)))))

(defn main []
  (let [[outcome payload] (read-catalog)]
    (if (= outcome :cannot-answer)
      (do (println payload) (finish! 2))
      (let [entries payload
            urls (vec (distinct (map :cite/url entries)))]
        (-> (reduce
             (fn [p u]
               (.then p (fn [acc]
                          (-> (fetch-url u)
                              (.then (fn [r] (assoc acc u r)))
                              (.catch (fn [err] (assoc acc u {:status 0 :chain []
                                                              :error (.-message err)})))
                              (.then (fn [acc2] (.then (sleep gap-ms) (fn [_] acc2))))))))
             (js/Promise.resolve {})
             urls)
            (.then
             (fn [fetched]
               (say "FETCHED" (count urls) "distinct url(s) for" (count entries) "row(s)")
               ;; A challenge is not an answer. Report and refuse before judging rows.
               (let [blocked (filterv (fn [u] (challenged? (get fetched u))) urls)]
                 (if (seq blocked)
                   (do (doseq [u blocked]
                         (println "BLOCKED" u "served a challenge page, not a record"))
                       (println "UNANSWERED" (count blocked) "of" (count urls)
                                "url(s) challenged the gate; refusing to report a pass or a drift")
                       (finish! 2))
                   (judge! entries fetched)))))
            (.catch
             (fn [err]
               (println "UNANSWERED" (.-message err))
               (finish! 2))))))))

(main)
