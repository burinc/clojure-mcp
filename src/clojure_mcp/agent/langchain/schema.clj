(ns clojure-mcp.agent.langchain.schema
  (:require
   [clojure.string :as string])
  (:import
   [dev.langchain4j.model.chat.request.json
    JsonAnyOfSchema
    JsonArraySchema
    JsonBooleanSchema
    JsonEnumSchema
    JsonIntegerSchema
    JsonNumberSchema
    JsonObjectSchema
    JsonStringSchema]))

(defmulti edn->sch
  (fn [{:keys [type enum] :as json-edn}]
    (cond
      (vector? type) :mixed-types
      (and type (keyword type)) (keyword type)
      enum :enum
      :else (throw (ex-info "By JSON data" {:json-edn json-edn})))))

(defn any-type-schema
  "Creates a schema that accepts any JSON type (except null)"
  []
  (-> (JsonAnyOfSchema/builder)
      (.anyOf [(.build (JsonStringSchema/builder))
               (.build (JsonNumberSchema/builder))
               (.build (JsonBooleanSchema/builder))
               (.build (JsonObjectSchema/builder))
               (-> (JsonArraySchema/builder)
                   (.items (.build (JsonStringSchema/builder))) ; Simple array of strings as default
                   .build)])
      .build))

(defmethod edn->sch :mixed-types [{:keys [type description]}]
  (let [schemas (keep (fn [t]
                        (cond
                          (= t "null") nil ; Skip null for now due to instantiation issues
                          (= t "object") (.build (JsonObjectSchema/builder)) ; Create empty object schema
                          ;; For array, create one that accepts any type of items
                          (= t "array") (-> (JsonArraySchema/builder)
                                            (.items (any-type-schema))
                                            .build)
                          :else (edn->sch {:type (keyword t)})))
                      type)]
    (cond-> (JsonAnyOfSchema/builder)
      description (.description description)
      :always (.anyOf schemas)
      :always (.build))))

(defmethod edn->sch :string [{:keys [description]}]
  (cond-> (JsonStringSchema/builder)
    description (.description description)
    :always (.build)))

(defmethod edn->sch :number [{:keys [description]}]
  (cond-> (JsonNumberSchema/builder)
    description (.description description)
    :always (.build)))

(defmethod edn->sch :integer [{:keys [description]}]
  (cond-> (JsonIntegerSchema/builder)
    description (.description description)
    :always (.build)))

(defmethod edn->sch :boolean [{:keys [description]}]
  (cond-> (JsonBooleanSchema/builder)
    description (.description description)
    :always (.build)))

;; Note: JsonNullSchema doesn't have a builder pattern in langchain4j
;; If you need to support null, handle it within JsonAnyOfSchema
;; or check langchain4j documentation for the correct instantiation method

(defmethod edn->sch :enum [{:keys [enum]}]
  (assert (every? string? enum))
  (assert (not-empty enum))
  (-> (JsonEnumSchema/builder)
      (.enumValues (map name enum))
      (.build)))

(defmethod edn->sch :array [{:keys [items]}]
  (assert items)
  (-> (JsonArraySchema/builder)
      (.items (edn->sch items))
      (.build)))

(defmethod edn->sch :object [{:keys [properties description required] :as object}]
  (let [obj-build
        (cond-> (JsonObjectSchema/builder)
          (not (string/blank? description)) (.description description)
          (not-empty required) (.required (map name required)))]
    (doseq [[nm edn-schema] properties]
      (.addProperty obj-build (name nm) (edn->sch edn-schema)))
    (.build obj-build)))
