#!/usr/bin/env bb

(require
 '[clojure.string :as str]
 '[clojure.tools.cli :refer [parse-opts]]
 '[babashka.http-client :as http]
 '[cheshire.core :as json])

(def cli-options
  [["-k" "--api-key KEY" "API Key"
    :default "2pgwhmg949mcuu5txj85qwts"]
   ["-e" "--env ENV" "Environment (e.g., sit, uat, prod)"
    :default "sit"]
   ["-p" "--endpoint ENDPOINT" "API endpoint (e.g., articles)"
    :default "articles"]
   ["-h" "--host HOST" "Host URL"
    :default "http://localhost:8003"]
   [nil "--page PAGE" "Page number for pagination"
    :default 1
    :parse-fn #(Integer/parseInt %)
    :validate [#(> % 0) "Must be a positive number"]]
   [nil "--size SIZE" "Page size for pagination"
    :default 1
    :parse-fn #(Integer/parseInt %)
    :validate [#(> % 0) "Must be a positive number"]]
   ["-?" "--help" "Show help"]])

(defn print-help [summary]
  (println "Usage: ./api-test.bb [options] [--extraKey extraValue ...]")
  (println)
  (println "Options:")
  (println summary)
  (println)
  (println "Additional Query Parameters:")
  (println "  Any additional --key value pairs will be added as query parameters")
  (println)
  (println "Examples:")
  (println "  ./api-test.bb                                    # Use all defaults")
  (println "  ./api-test.bb --page 1 --size 1                  # Custom pagination")
  (println "  ./api-test.bb --env uat --endpoint users         # Different env and endpoint")
  (println "  ./api-test.bb -k 'new-key' --env prod            # Override API key and environment")
  (println "  ./api-test.bb --fromDate '2025-08-31T11:27:29.651Z' --toDate '2025-09-01T11:27:29.651Z'")
  (println "  ./api-test.bb --page 2 --size 10 --category sports --status published"))

(defn parse-extra-params
  "Parse remaining arguments as --key value pairs for extra query parameters"
  [args]
  (loop [remaining args
         params {}]
    (if (empty? remaining)
      params
      (let [[arg & rest] remaining]
        (if (and (str/starts-with? arg "--") (seq rest))
          (let [key-name (subs arg 2) ; Remove the "--" prefix
                [value & more] rest]
            (recur more (assoc params (keyword key-name) value)))
          ;; Skip arguments that don't match the pattern
          (recur rest params))))))

(defn build-query-string [params]
  (when (seq params)
    (str "?"
         (clojure.string/join "&"
                              (map (fn [[k v]] (str (name k) "=" v))
                                   params)))))

(defn test-endpoint [{:keys [api-key env endpoint host page size]} extra-params]
  (let [base-url (str host "/proxy/" env "/v3")
        ;; Merge standard params with extra params
        query-params (merge {:page page :size size} extra-params)
        query-string (build-query-string query-params)
        full-url (str base-url "/" endpoint query-string)]

    (println "=========================================")
    (println "Testing API Endpoint")
    (println "=========================================")
    (println)
    (println (str "Environment: " env))
    (println (str "Endpoint: " endpoint))
    (println "Query Parameters:")
    (doseq [[k v] query-params]
      (println (str "  " (name k) ": " v)))
    (println (str "URL: " full-url))
    (println "-----------------------------------------")

    (try
      (let [response (http/get full-url
                               {:headers {"X-NewsApi-Api-Key" api-key}
                                :throw false})
            status (:status response)]

        (if (= 200 status)
          (let [body (json/parse-string (:body response) true)
                ; Get only first level keys
                first-level-keys (keys body)
                results (:results body)]

            (println)
            (println "First level data structure:")
            (doseq [k first-level-keys]
              (let [v (get body k)
                    value-info (cond
                                 (sequential? v) (str "array with " (count v) " items")
                                 (map? v) (str "object with " (count v) " keys")
                                 (number? v) (str v)
                                 (string? v) (str "\"" v "\"")
                                 (boolean? v) (str v)
                                 (nil? v) "null"
                                 :else (str v))]
                (println (str "  " (name k) ": " value-info))))

            ; Show pagination info if available
            (when-let [total (:total body)]
              (println)
              (println "Pagination info:")
              (println (str "  Total items: " total))
              (println (str "  Current page: " page))
              (println (str "  Page size: " size))
              (when (> total 0)
                (println (str "  Total pages: " (Math/ceil (/ total (double size)))))))

            (println)
            (if results
              (do
                (println (str "✅ Found " (count results) " " endpoint " on this page"))
                ; Optionally show first item's structure
                (when (seq results)
                  (println)
                  (println "First item's keys:")
                  (doseq [k (keys (first results))]
                    (println (str "  - " (name k))))))
              (println (str "❌ No results found in response")))

            ; Pretty print the full JSON response
            (println)
            (println "=========================================")
            (println "Full JSON Response:")
            (println "=========================================")
            (println (json/generate-string body {:pretty true})))

          (do
            (println (str "❌ Request failed with status: " status))
            (when-let [body (:body response)]
              (try
                (let [error-body (json/parse-string body true)]
                  (println "Error response:")
                  (println (json/generate-string error-body {:pretty true})))
                (catch Exception _
                  (println "Raw error:" body)))))))

      (catch Exception e
        (println (str "❌ Error: " (.getMessage e)))))))

(defn -main [args] ; Note: no & here - args is already a vector
  (let [{:keys [options arguments errors summary]} (parse-opts args cli-options :in-order true)
        ;; Parse any remaining arguments as extra query parameters
        extra-params (parse-extra-params arguments)]
    (cond
      (:help options)
      (print-help summary)

      errors
      (do
        (println "Errors:")
        (doseq [error errors]
          (println "  " error))
        (println)
        (print-help summary)
        (System/exit 1))

      :else
      (test-endpoint options extra-params))))

;; Pass the args as a vector directly
(-main *command-line-args*)