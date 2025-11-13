(ns clojure-mcp.tools.bash.tool-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure-mcp.tools.bash.tool :as tool]
            [clojure-mcp.config :as config]))

(deftest test-create-bash-tool-with-config
  (testing "Tool creation with timeout configuration"
    (let [nrepl-client-atom (atom {::config/config
                                   {:tools-config {:bash {:default-timeout-ms 60000}}
                                    :nrepl-user-dir "/tmp"}})
          tool-config (tool/create-bash-tool nrepl-client-atom)]
      (is (= :bash (:tool-type tool-config)))
      (is (= 60000 (:timeout_ms tool-config))
          "Timeout should be set from config")))

  (testing "Tool creation with working directory configuration"
    (let [nrepl-client-atom (atom {::config/config
                                   {:tools-config {:bash {:working-dir "/opt/project"}}
                                    :nrepl-user-dir "/tmp"}})
          tool-config (tool/create-bash-tool nrepl-client-atom)]
      (is (= :bash (:tool-type tool-config)))
      (is (= "/opt/project" (:working-dir tool-config))
          "Working directory should be set from config")))

  (testing "Tool creation with bash-over-nrepl from tool config"
    (let [nrepl-client-atom (atom {::config/config
                                   {:tools-config {:bash {:bash-over-nrepl false}}
                                    :bash-over-nrepl true ; Global config says true
                                    :nrepl-user-dir "/tmp"}})
          tool-config (tool/create-bash-tool nrepl-client-atom)]
      (is (= :bash (:tool-type tool-config)))
      (is (nil? (:nrepl-session tool-config))
          "Session should be nil when tool config overrides to false")))

  (testing "Tool creation with multiple config options"
    (let [nrepl-client-atom (atom {::config/config
                                   {:tools-config {:bash {:default-timeout-ms 120000
                                                          :working-dir "/home/user"
                                                          :bash-over-nrepl true}}
                                    :nrepl-user-dir "/tmp"}})
          tool-config (tool/create-bash-tool nrepl-client-atom)]
      (is (= :bash (:tool-type tool-config)))
      (is (= 120000 (:timeout_ms tool-config)))
      (is (= "/home/user" (:working-dir tool-config)))))

  (testing "Tool creation without config uses defaults"
    (let [nrepl-client-atom (atom {::config/config
                                   {:nrepl-user-dir "/tmp"}})
          tool-config (tool/create-bash-tool nrepl-client-atom)]
      (is (= :bash (:tool-type tool-config)))
      (is (= 180000 (:timeout_ms tool-config))
          "Default timeout should be 180000ms")
      (is (= "/tmp" (:working-dir tool-config))
          "Working dir should be nrepl-user-dir")))

  (testing "Config key default-timeout-ms is not passed through"
    (let [nrepl-client-atom (atom {::config/config
                                   {:tools-config {:bash {:default-timeout-ms 90000
                                                          :some-other-key "value"}}
                                    :nrepl-user-dir "/tmp"}})
          tool-config (tool/create-bash-tool nrepl-client-atom)]
      (is (= 90000 (:timeout_ms tool-config))
          "default-timeout-ms should be converted to timeout_ms")
      (is (nil? (:default-timeout-ms tool-config))
          "default-timeout-ms should not be in final config")
      (is (= "value" (:some-other-key tool-config))
          "Other config keys should pass through"))))