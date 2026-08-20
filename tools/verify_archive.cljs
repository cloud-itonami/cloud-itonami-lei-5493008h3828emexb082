#!/usr/bin/env nbb
;; Checks the archived document in 80-data/public/tos.journal.edn against itself:
;; the recorded sha256 must reproduce from the recorded text.
;;
;; This is deliberately NOT a network check. It answers one question -- "is this
;; capture internally consistent, or has the text been edited since it was
;; taken" -- and it must not be read as saying the source still serves that
;; document, or that the document is what its :tos/doc-type claims. A sibling
;; repository in this fleet holds a capture whose sha256 reproduces perfectly
;; and which is a 404 page.
;;
;; Exit codes:
;;   0 — checked, the digest reproduces
;;   1 — checked, the digest does not reproduce
;;   2 — could not check (file missing, unparseable, or a required field absent)

(ns verify-archive
  (:require [clojure.edn :as edn]
            [clojure.string :as str]
            ["fs" :as fs]
            ["crypto" :as crypto]))

(def argv (vec (drop 3 (js->clj js/process.argv))))
(def journal-path
  (or (first (remove #(str/starts-with? % "--") argv))
      "80-data/public/tos.journal.edn"))

(defn finish! [code msg]
  (println msg)
  (set! (.-exitCode js/process) code)
  (js/process.exit code))

(when-not (.existsSync fs journal-path)
  (finish! 2 (str "MISSING journal " journal-path)))

(let [parsed (try {:v (edn/read-string (.readFileSync fs journal-path "utf8"))}
                  (catch :default e {:err (.-message e)}))]
  (when (:err parsed) (finish! 2 (str "PARSE-FAIL " (:err parsed))))
  (let [datoms (:v parsed)
        _ (when-not (sequential? datoms) (finish! 2 "SHAPE journal is not a sequence of datoms"))
        by-attr (reduce (fn [m d] (if (and (sequential? d) (<= 3 (count d)))
                                    (assoc m (nth d 1) (nth d 2)) m))
                        {} datoms)
        text (get by-attr :tos/full-text)
        recorded (get by-attr :tos/sha256)]
    (cond
      (not (string? text))     (finish! 2 "MISSING :tos/full-text")
      (not (string? recorded)) (finish! 2 "MISSING :tos/sha256")
      :else
      (let [computed (-> (crypto/createHash "sha256") (.update text "utf8") (.digest "hex"))]
        (println "CHARS" (count text))
        (println "RECORDED" recorded)
        (println "COMPUTED" computed)
        (if (= computed recorded)
          (finish! 0 "PASS digest reproduces from the recorded text")
          (finish! 1 "MISMATCH the recorded text does not hash to the recorded digest"))))))
