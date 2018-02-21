(ns ledger-reconciler.core
  (:require [clojure.string :as string]
            [clojure.data.csv :as csv]
            [clojure.java.shell :refer [sh]])
  (:gen-class))

(defn parse-dcu-csv [filename]
  (->> (slurp filename)
       (csv/read-csv)
       (drop 4)
       (map (fn [row]
              (let [date (nth row 1)
                    description (nth row 3)
                    debit (nth row 4)
                    credit (nth row 5)]
                (if (not (string/blank? debit))
                  {:date date :description description :amount debit}
                  {:date date :description description :amount credit}))))))

(defn parse-ledger-row [row]
  (let [row-vec (string/split row #"\s\s+")]
    {:date (subs (nth row-vec 0) 0 9)
     :description (subs (nth row-vec 0) 10)
     :amount (subs (nth row-vec 2) 1)}))

(defn parse-ledger-output [period account]
  (->> (sh "ledger" "-w" "-p" period "reg" account)
      (:out)
      (#(string/split % #"\n"))
      (map parse-ledger-row)
      (reverse)))

(defn remove-one [pred coll]
  (let [[f r] (split-with #(not (pred %)) coll)]
    (concat f (rest r))))

(defn matching-amount? [item [record-first & record-rest]]
  (cond
    (nil? record-first) false
    (= (:amount item) (:amount record-first)) true
    :default (matching-amount? item record-rest)))

(defn compare-records [[h1 & r1 :as record1] record2 acc1 acc2]
  (cond
    (nil? h1) (list acc1 (apply conj acc2 record2))
    (empty? record2) (list (apply conj acc1 record1) acc2)
    :else (if (matching-amount? h1 record2)
            (compare-records r1 (remove-one #(= (:amount %) (:amount h1)) record2) acc1 acc2)
            (compare-records r1 record2 (conj acc1 h1) acc2))))

(defn format-item [item]
  (format "%-15s %-10s %-20s" (:date item) (:amount item) (:description item)))

(defn -main
  [& args]
  (let [[period account filename] args
        dcu-record (parse-dcu-csv filename)
        ledger-record (parse-ledger-output period account)
        [dcu-unmatched ledger-unmatched] (compare-records dcu-record ledger-record '() '())]
    (if (> (+ (count dcu-unmatched) (count ledger-unmatched)) 0)
      (do (println "Unmatched bank transactions:")
          (doseq [t dcu-unmatched] (println (format-item t)))
          (print "\n")
          (println "Unmatched Ledger transactions:")
          (doseq [t ledger-unmatched] (println (format-item t)))
          (System/exit 0))
      (println "No discrepancies found"))))
