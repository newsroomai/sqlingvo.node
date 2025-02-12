(ns sqlingvo.node.async-test
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [cljs.core.async :as async]
            [clojure.pprint :refer [pprint]]
            [clojure.string :as str]
            [clojure.test :refer [async deftest is testing]]
            [sqlingvo.core :as sql]
            [sqlingvo.node.async :as node :refer-macros [<? <!?]]))

(defn underscore [s]
  (str/replace (name s) "-" "_"))

(defn hypenate [s]
  (str/replace (name s) "_" "-"))

(def db (node/db "postgresql://tiger:scotch@localhost/sqlingvo_node"
                 {:sql-name underscore
                  :sql-identifier hypenate}))

(deftest test-db-spec
  (is (fn? (:sql-name db)))
  (is (fn? (:sql-identifier db))))

(def country-data
  [{:code "de" :name "Germany"}
   {:code "es" :name "Spain"}])

(defn create-countries
  "Create the countries table."
  [db]
  (sql/create-table db :countries
    (sql/column :id :serial :primary-key? true)
    (sql/column :name :text :unique? true)
    (sql/column :code :text)))

(defn drop-countries
  "Drop the countries table."
  [db]
  (sql/drop-table
      db [:countries]
    (sql/if-exists true)))

(defn insert-countries
  "Drop the countries table."
  [db]
  (sql/insert db :countries []
    (sql/values country-data)
    (sql/returning :*)))

(defn countries
  "Return all countries."
  [db]
  (sql/select db [:*]
    (sql/from :countries)))

(deftest test-connect
  (async done
    (go (try
          (let [db (<? (node/connect db))]
            (is (:connection db))
            (node/disconnect db)
            (done))
          (catch js/Error e
            (prn (prn (.-stack e))))))))

(deftest test-connect-error
  (async done
    (go (try [db (<? (node/connect (node/db "postgresql://localhost/unknown_db")))]
             (assert false "Connection error expected.")
             (catch js/Error e
               (let [message (str/replace (.-message e) #"\n" "")]
                 (is (re-matches #".*does not exist.*" message))))
             (finally (done))))))

(deftest test-disconnect
  (async done
    (go (let [db (node/disconnect (<? (node/connect db)))]
          (is (nil? (:connection db)))
          (done)))))

(deftest test-run-queries
  (async done
    (go (let [db (<? (node/connect db))
              _ (<!? (drop-countries db))
              _ (<!? (create-countries db))]
          (is (= (<!? (insert-countries db))
                 (<!? (countries db))))
          _ (<!? (drop-countries db))
          (done)))))

(def pg-statistics
  {:commit-action nil,
   :reference-generation nil,
   :is-typed "NO",
   :is-insertable-into "YES",
   :table-catalog "sqlingvo_node",
   :user-defined-type-schema nil,
   :user-defined-type-name nil,
   :user-defined-type-catalog nil,
   :table-schema "pg_catalog",
   :self-referencing-column-name nil,
   :table-name "pg_statistic",
   :table-type "BASE TABLE"})

(defn- select-pg-statistics [db]
  (sql/select db [:*]
    (sql/from :information_schema.tables)
    (sql/where '(= :table_name "pg_statistic"))))

(deftest test-hyphenate
  (async done
    (go (let [db (<? (node/connect db))]
          (is (= (<!? (select-pg-statistics db))
                 [pg-statistics]))
          (<? (node/disconnect db))
          (done)))))

(deftest test-pool
  (async done
    (let [db (node/start db)
          ^js pool (:pool db)]
      (go
        (is pool)
        (is (= (.-totalCount pool) 0))
        (testing "use first available client"
          (is (= (<!? (select-pg-statistics db))
                 [pg-statistics])))
        (testing "connect client and release"
          (let [db (<? (node/connect db))]
            (is (:connection db))
            (is (= (.-totalCount pool) 1))
            (is (= (<!? (select-pg-statistics db))
                   [pg-statistics]))
            (let [db (<? (node/disconnect db))]
              (is (nil? (:connection db)))
              (let [db (<? (node/stop db))]
                (is (nil? (:pool db)))
                (done)))))))))
