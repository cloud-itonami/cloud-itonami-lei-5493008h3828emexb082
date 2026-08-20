#!/usr/bin/env nbb
;; Live gate for facts/catalog.edn (organisation-record citations).
;;
;; For every :catalog/entries row:
;;   * GET :cite/url
;;   * require HTTP 2xx
;;   * if :cite/expect-substring is non-empty, require it in the body
;;
;; Exit codes:
;;   0 — answered, every citation checked, floor met
;;   1 — answered, at least one citation wrong
;;   2 — could not answer (parse failure, network, zero checks, floor miss)
;;
;; "Nothing was checked" and "nothing was wrong" must not share an exit code.

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

(defn fetch-url
  ([u] (fetch-url u 0))
  ([u hops]
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
                             (-> (fetch-url (url/resolve u loc) (inc hops))
                                 (.then resolve)
                                 (.catch reject)))
                         (let [chunks #js []]
                           (.on res "data" (fn [c] (.push chunks c)))
                           (.on res "end"
                                (fn []
                                  (resolve {:status status
                                            :body (.toString (js/Buffer.concat chunks)
                                                             "utf8")})))
                           (.on res "error" reject))))))]
          (.on req "error" reject)
          (.on req "timeout" (fn [] (.destroy req) (reject (js/Error. "timeout"))))
          (.end req))
        (catch :default e (reject e)))))))

(defn check-entry! [e]
  (-> (fetch-url (:cite/url e))
      (.then
       (fn [{:keys [status body]}]
         (let [expect (or (:cite/expect-substring e) "")
               ok-status (<= 200 status 299)
               ok-body (or (str/blank? expect)
                           (str/includes? (or body "") expect))]
           (cond
             (not ok-status)
             {:ok? false :id (:cite/id e) :why (str "HTTP " status)}

             (not ok-body)
             {:ok? false :id (:cite/id e)
              :why (str "missing substring " (pr-str expect))}

             :else
             {:ok? true :id (:cite/id e) :status status}))))
      (.catch
       (fn [err]
         {:ok? false :id (:cite/id e) :why (str "fetch-error " (.-message err))}))))

(defn finish! [code]
  (set! (.-exitCode js/process) code)
  ;; Give pending console I/O a tick, then exit hard so nbb cannot report 0
  ;; after an async failure (measured: process.exit from a .then raced the
  ;; runtime teardown and the shell saw 0 while DRIFT lines had already printed).
  (js/setTimeout (fn [] (js/process.exit code)) 50))

(defn main []
  (when-not (.existsSync fs catalog-path)
    (println "MISSING catalog" catalog-path)
    (finish! 2)
    nil)
  (let [raw (try (edn/read-string (.readFileSync fs catalog-path "utf8"))
                 (catch :default e
                   (println "PARSE-FAIL" (.-message e))
                   (finish! 2)
                   nil))
        entries (when raw (:catalog/entries raw))]
    (when-not (seq entries)
      (println "EMPTY catalog entries")
      (finish! 2))
    (when (seq entries)
      (-> (reduce
           (fn [p e]
             (-> p
                 (.then (fn [acc]
                          (-> (check-entry! e)
                              (.then (fn [r]
                                       (say (if (:ok? r) "OK" "FAIL")
                                            (:id r)
                                            (or (:why r) (:status r)))
                                       (conj acc r)))
                              (.then (fn [acc2]
                                       (-> (sleep gap-ms)
                                           (.then (fn [_] acc2))))))))))
           (js/Promise.resolve [])
           entries)
          (.then
           (fn [results]
             (let [n (count results)
                   bad (filterv (complement :ok?) results)
                   ok-n (- n (count bad))]
               (say "CHECKED" n "OK" ok-n "FAIL" (count bad) "MIN" min-citations)
               (cond
                 (zero? n)
                 (do (println "FLOOR zero checks") (finish! 2))

                 (< n min-citations)
                 (do (println "FLOOR below --min" min-citations "got" n)
                     (finish! 2))

                 (seq bad)
                 (do (doseq [b bad] (println "DRIFT" (:id b) (:why b)))
                     (finish! 1))

                 :else
                 (do (say "PASS") (finish! 0))))))
          (.catch
           (fn [err]
             (println "UNANSWERED" (.-message err))
             (finish! 2)))))))

(main)
