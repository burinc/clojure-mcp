(ns clojure-mcp.tools.file-edit.core
  "Core implementation for the file-edit tool.
   This namespace contains the pure functionality without any MCP-specific code."
  (:require
   [clojure.java.io :as io]
   [clojure.string :as str]
   [clojure-mcp.utils.file :as file-utils]))

(defn load-file-content
  "Load file content from a path.
   
   Parameters:
   - path: Path to the file to load
   
   Returns a map with:
   - :content - The file contents as a string
   - :error - Boolean indicating if an error occurred
   - :message - Error message (only when :error is true)"
  [path]
  (try
    (let [file (io/file path)]
      (if (.exists file)
        {:content (file-utils/slurp-utf8 file)
         :error false}
        {:error true
         :message (str "File not found: " path)}))
    (catch Exception e
      {:error true
       :message (str "Error reading file: " (.getMessage e))})))

(defn count-occurrences
  "Count occurrences of a substring within a string."
  [string substring]
  (if (empty? substring)
    0
    (count (re-seq (re-pattern (java.util.regex.Pattern/quote substring)) string))))

(defn suggest-similar-file
  "Try to find a similar file with a different extension.
   Return nil if no similar file found."
  [file-path]
  (let [f (io/file file-path)
        dir (.getParent f)
        name-without-ext (second (re-find #"(.+?)(?:\.[^.]*$|$)" (.getName f)))
        dir-file (io/file dir)
        similar-files (when (and dir-file (.exists dir-file) (.isDirectory dir-file))
                        (->> (.listFiles dir-file)
                             (filter #(.isFile %))
                             (filter #(str/starts-with? (.getName %) name-without-ext))
                             (filter #(not= (.getAbsolutePath %) file-path))))]
    (when (seq similar-files)
      (.getAbsolutePath (first similar-files)))))

(defn validate-file-edit
  "Validate a file edit operation.
   
   Parameters:
   - file-path: The path to the file
   - old-string: The string to replace
   - new-string: The replacement string
   - file-content: The current file content (or nil if file doesn't exist)
   
   Returns a map with:
   - :valid - Boolean indicating if the edit is valid
   - :message - Error message (only present when :valid is false)"
  [file-path old-string new-string file-content]
  (cond
    ;; Case 1: No changes (old-string and new-string are identical)
    (= old-string new-string)
    {:valid false
     :message "No changes to make: old_string and new_string are exactly the same."}

    ;; Case 2: Empty old-string (was previously used for new file creation)
    (empty? old-string)
    {:valid false
     :message "Empty old_string is not supported. To create a new file, use file_write instead."}

    ;; Case 3: File doesn't exist but trying to edit it
    (nil? file-content)
    (let [similar-file (suggest-similar-file file-path)
          message (str "File does not exist."
                       (when similar-file (str " Did you mean " similar-file "?")))]
      {:valid false
       :message message})

    ;; Case 4: No matches of old-string in file
    (not (str/includes? file-content old-string))
    {:valid false
     :message "String to replace not found in file."}

    ;; Case 5: Multiple matches of old-string (not unique)
    (> (count-occurrences file-content old-string) 1)
    (let [matches (count-occurrences file-content old-string)]
      {:valid false
       :message (str "Found " matches " matches of the string to replace. "
                     "For safety, this tool only supports replacing exactly one occurrence at a time. "
                     "Add more lines of context to your edit and try again.")})

    ;; Case 6: Valid edit
    :else
    {:valid true}))

(defn perform-file-edit
  "Perform the actual file edit operation.
   
   Parameters:
   - file-path: The path to the file
   - old-string: The string to replace
   - new-string: The replacement string
   - file-content: The current file content

   Returns the new file content."
  [_file-path old-string new-string file-content]
  ;; Edit existing file
  (str/replace-first file-content old-string new-string))

(defn save-file-content
  "Save content to a file, creating parent directories if needed.
   
   Parameters:
   - file-path: Path to the file to save
   - content: Content to save
   
   Returns a map with:
   - :success - Boolean indicating if the save was successful
   - :message - Error message (only when :success is false)"
  [file-path content]
  (try
    (let [file (io/file file-path)
          parent (.getParentFile file)]
      ;; Create parent directories if they don't exist
      (when (and parent (not (.exists parent)))
        (.mkdirs parent))
      (file-utils/spit-utf8 file content)
      {:success true})
    (catch Exception e
      {:success false
       :message (str "Failed to save file: " (.getMessage e))})))