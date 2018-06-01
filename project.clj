(defproject ledger-reconciler "0.1.0-SNAPSHOT"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/data.csv "0.1.4"]
                 [org.clojure/tools.cli "0.3.7"]]
  :uberjar-name "ledger-reconciler.jar"
  :target-path "target"
  :profiles {:uberjar {:aot :all}}
  :main ledger-reconciler.core
  :plugins [[lein-bin "0.3.4"]]
  :bin {:name "ledger-reconciler"})
