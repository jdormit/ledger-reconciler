(ns ledger-reconciler.core
  (:require [clojure.string :as string]
            [clojure.data.csv :as csv])
  (:gen-class))

(defn parse-dcu-csv [filename]
  (->> (slurp filename)
       (csv/read-csv)
       (drop 4)
       (map (fn [row]
              (let [debit (nth row 4)
                    credit (nth row 5)]
                (if (not (string/blank? debit))
                  debit
                  credit))))
       (map read-string)))

(defn -main
  [& args]
  (prn (apply parse-dcu-csv args)))
