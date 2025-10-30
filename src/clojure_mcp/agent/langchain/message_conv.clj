(ns clojure-mcp.agent.langchain.message-conv
  "Provides round-trip conversion between LangChain4j ChatMessage lists and EDN data.
   
   This allows for:
   - Converting List<ChatMessage> to EDN for manipulation
   - Modifying messages in EDN format
   - Converting EDN back to List<ChatMessage>
   
   The conversion preserves all message content and allows for easy manipulation
   of message metadata in pure Clojure data structures."
  (:require
   [clojure.data.json :as json])
  (:import
   [dev.langchain4j.data.message
    UserMessage
    AiMessage
    SystemMessage
    ToolExecutionResultMessage
    ChatMessageSerializer
    ChatMessageDeserializer]))

(defn messages->edn [msgs]
  (-> (ChatMessageSerializer/messagesToJson msgs)
      (json/read-str :key-fn keyword))) ; => EDN vector with keywords

(defn edn->messages [edn-msgs]
  (-> edn-msgs
      json/write-str
      (ChatMessageDeserializer/messagesFromJson)))

(defn message->edn [java-msg]
  (some-> java-msg
          list
          messages->edn
          first))

(defn edn->message [edn-msg]
  (some-> edn-msg
          list
          edn->messages
          first))

(defn parse-tool-arguments
  "Parse JSON string arguments in toolExecutionRequests to EDN.
   Makes REPL testing easier by converting arguments from strings to maps."
  [ai-message]
  (if-let [requests (:toolExecutionRequests ai-message)]
    (assoc ai-message
           :toolExecutionRequests
           (mapv (fn [req]
                   (update req :arguments
                           (fn [args]
                             (if (string? args)
                               (json/read-str args :key-fn keyword)
                               args))))
                 requests))
    ai-message))

(defn parse-messages-tool-arguments
  "Parse tool arguments in all AI messages within a message list"
  [messages]
  (mapv (fn [msg]
          (if (= "AI" (:type msg))
            (parse-tool-arguments msg)
            msg))
        messages))

;; AiMessage
;; {:text "Hello", :toolExecutionRequests [], :type "AI"}

;; UserMessage 
;; {:contents [{:text "Hello", :type "TEXT"}], :type "USER"}

;; SystemMessage
;; {:text "Hello", :type "SYSTEM"}

;; {:toolExecutionRequests [{:id "call_KLFov8DW5Ar2EOHNMPtzFlQJ",
;;                           :name "think",
;;                           :arguments "{\"thought\":\"First thought: Evaluate the basic arithmetic problem of adding 2 and 2, combining two discrete units with another two discrete units.\"}"}],
;;  :type "AI"}

;; ToolExecutionResultMessage
;; {:id "idasdf",
;;  :toolName "think",
;;  :text "your thought has been logged",
;;  :type "TOOL_EXECUTION_RESULT"}

#_(-> (SystemMessage/from "Hello")
      message->edn)

(defn chat-request->edn
  "Convert a ChatRequest to EDN data for storage and manipulation."
  [chat-request]
  {:messages (messages->edn (.messages chat-request))
   :maxTokens (.maxTokens chat-request)
   :temperature (.temperature chat-request)
   :topP (.topP chat-request)})

(defn edn->chat-request
  "Convert EDN data back to a ChatRequest.
   
   Note: This creates a ChatRequest builder - caller must build() it."
  [{:keys [messages maxTokens temperature topP]}]
  (cond-> (dev.langchain4j.model.chat.request.ChatRequest/builder)
    messages (.messages (edn->messages messages))
    maxTokens (.maxTokens maxTokens)
    temperature (.temperature temperature)
    topP (.topP topP)))
