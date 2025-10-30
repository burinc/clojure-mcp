(ns clojure-mcp.tool-format
  "Tool execution request and result formatting"
  (:require [clojure.string :as str]
            [clj-commons.ansi :as ansi]))

(def default-result-lines 5)

;; Helper functions for ANSI formatting

(defn format-kv
  "Format a key-value pair with ANSI styling"
  [k v]
  (ansi/compose [:faint (str (name k) ":")] " " [:bold.white (str v)]))

(defn format-args-box
  "Format arguments as box lines (without opening or closing)"
  [args-map]
  (map (fn [[k v]]
         (ansi/compose [:cyan "│ "] (format-kv k v)))
       args-map))

;; Tool request formatting (opens box, doesn't close it)

(defmulti format-tool-request
  "Format a tool execution request for display.
   Opens a cyan box with tool name and arguments but doesn't close it."
  (comp keyword :name))

(defmethod format-tool-request :default
  [{:keys [name arguments]}]
  (let [header (ansi/compose [:bold.cyan "╭─ " [:white (str name)]])
        args-lines (format-args-box arguments)]
    (str/join "\n" (concat [header] args-lines))))

(defmethod format-tool-request :glob_files
  [{:keys [name arguments]}]
  (let [header (ansi/compose [:bold.cyan "╭─ " [:white (str name)]])
        args-lines (format-args-box arguments)]
    (str/join "\n" (concat [header] args-lines))))

(defmethod format-tool-request :grep
  [{:keys [name arguments]}]
  (let [header (ansi/compose [:bold.cyan "╭─ " [:white (str name)]])
        args-lines (format-args-box arguments)]
    (str/join "\n" (concat [header] args-lines))))

(defmethod format-tool-request :read_file
  [{:keys [name arguments]}]
  (let [header (ansi/compose [:bold.cyan "╭─ " [:white (str name)]])
        args-lines (format-args-box arguments)]
    (str/join "\n" (concat [header] args-lines))))

(defmethod format-tool-request :clojure_edit
  [{:keys [name arguments]}]
  (let [{:keys [operation form_type form_identifier file_path content]} arguments
        header (ansi/compose [:bold.cyan "╭─ "
                              [:bold.white (str name)]
                              [:plain.cyan (str "(" operation ", " form_type ", " form_identifier ")")]])
        file-line (ansi/compose [:cyan "│ "] (format-kv "file_path" file_path))
        content-lines (str/split-lines content)
        single-line? (= 1 (count content-lines))
        content-display (if single-line?
                          [(ansi/compose [:cyan "│ "] (format-kv "content" (first content-lines)))]
                          (concat [(ansi/compose [:cyan "│ "] [:faint "content:"])]
                                  (map (fn [line]
                                         (ansi/compose [:cyan "│ "] line))
                                       content-lines)))]
    (str/join "\n" (concat [header file-line] content-display))))

;; Tool result formatting (connects to open box and closes it)

(defmulti format-tool-result
  "Format a tool execution result for display.
   Connects to an open box with ├─ and closes with ╰─"
  (comp keyword :toolName))

(defmethod format-tool-result :default
  [{:keys [text]}]
  (let [lines (str/split-lines text)
        line-count (count lines)
        truncated? (> line-count default-result-lines)
        display-lines (if truncated?
                        (take default-result-lines lines)
                        lines)
        connector (ansi/compose [:bold.cyan "├─ Result"])
        body-lines (map (fn [line]
                          (ansi/compose [:cyan "│ "] line))
                        display-lines)
        ellipsis (when truncated?
                   (ansi/compose [:cyan "│ "] [:faint "... (" (- line-count default-result-lines) " more lines)"]))
        footer (ansi/compose [:cyan "╰─"])]
    (str/join "\n" (concat [connector]
                           body-lines
                           (when ellipsis [ellipsis])
                           [footer]))))
