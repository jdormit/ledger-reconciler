(ns ledger-reconciler.core
  (:require [clojure.string :as string]
            [clojure.data.csv :as csv]
            [clojure.java.shell :refer [sh]]
            [clojure.tools.cli :as cli]
            [clojure.edn :as edn])
  (:gen-class))

(defn nth-or [pred v & xs]
  (when xs
    (if-let [r (get v (first xs))]
      (if (pred r) r (apply nth-or pred v (rest xs)))
      (apply nth-or pred v (rest xs)))))

(defn to-seq [x]
  (if (coll? x) (seq x) (seq (list x))))

(defn parse-bank-csv [& {:keys [filename
                                date
                                description
                                amount
                                header-rows]
                         :or {header-rows 1}}]
  (->> (slurp filename)
       (csv/read-csv)
       (drop header-rows)
       (map (fn [row]
              (let [row-nth-or (partial nth-or
                                        #(not (string/blank? %))
                                        row)]
                {:date (apply row-nth-or (to-seq date))
                 :description (apply row-nth-or (to-seq description))
                 :amount (apply row-nth-or (to-seq amount))})))))

(defn parse-ledger-row [row]
  (let [row-vec (string/split row #"\s\s+")]
    {:date (subs (row-vec 0) 0 9)
     :description (subs (row-vec 0) 10)
     :amount (subs (row-vec 2) 1)}))

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

(def cli-options
  [["-p" "--period PERIOD" "Ledger period expression"]
   ["-a" "--account ACCOUNT" "Ledger account expression"]
   ["-t" "--type TYPE" "Type of bank account as configured in init file"]
   ["-i" "--init-file FILE" "Path to init file"
    :default (str (System/getProperty "user.home")
                  (java.io.File/separator)
                  ".ledgerreconciler.edn")]])

(defn validate-args
  [args]
  (let [{:keys [options arguments summary errors]} (cli/parse-opts args cli-options)]
   (if errors
     {:error (string/join \newline errors)}
     (assoc options :filename (first arguments)))))

(defn make-parse-fn
  [config-file type]
  (let [config (slurp config-file)
        {:keys [date description amount header-rows]
         :or {header-rows 1}}
        ((keyword type) (edn/read-string config))]
    (partial parse-bank-csv
             :date date
             :description description
             :amount amount
             :header-rows header-rows)))

(defn -main
  [& args]
  (let [{:keys [period account type filename init-file error]} (validate-args args)]
    (if error
      (do (println error)
          (System/exit 1))
      (let [parse-fn (make-parse-fn init-file type)
            bank-record (parse-fn :filename filename)
            ledger-record (parse-ledger-output period account)
            [bank-unmatched ledger-unmatched] (compare-records
                                               (reverse bank-record)
                                               (reverse ledger-record)
                                               '() '())]
        (if (> (+ (count bank-unmatched) (count ledger-unmatched)) 0)
          (doseq [unmatched [["bank" bank-unmatched] ["Ledger" ledger-unmatched]]]
            (when (> (count (unmatched 1)) 0)
              (do (println (str "Unmatched " (unmatched 0) " transactions:"))
                  (doseq [t (unmatched 1)] (println (format-item t)))
                  (print "\n"))))
          (println "No discrepencies found"))
        (System/exit 0)))))
