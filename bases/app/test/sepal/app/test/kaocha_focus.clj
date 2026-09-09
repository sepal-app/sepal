(ns sepal.app.test.kaocha-focus
  "Make `--focus <namespace>` load only that namespace.

  Kaocha builds its test plan by loading every namespace matching a suite's
  `:ns-patterns`, then applies `--focus` to the plan. The built-in filter
  plugin prunes in `pre-load`, but only at the suite level — `:unit` against
  `:e2e` — so focusing one namespace still loads all 125 of them, and the first
  one to need it drags in the whole application.

  `:ns-patterns` is only a list of regex sources on the suite map, and
  `pre-load` may rewrite it. When `--focus` names namespaces and nothing else,
  this narrows every suite to exactly those namespaces. Focusing a component
  test that needs no application goes from 30 seconds to 9.

  It declines whenever narrowing could change which tests run, and falls back
  to loading everything. The rules are in `ns-focus-patterns`.

  It lives in the base's test tree rather than in a brick because the whole
  suite uses it and nothing may ship it: `components/test` is a dependency of
  `projects/app`, so a Kaocha require there would put the test runner in the
  uberjar."
  (:require [clojure.string :as str]
            [kaocha.plugin :as plugin]))

(defn- ns-pattern
  "A regex source matching `ns-name` and nothing else.

  Anchored and dot-escaped on purpose. `sepal.a-test` unescaped and unanchored
  also matches `other.sepal.a-test` and `sepalXa-test`, which would load
  namespaces the run did not ask for."
  [ns-name]
  (str "^" (str/replace ns-name "." "\\.") "$"))

(defn ns-focus-patterns
  "Regex sources matching exactly the namespaces `focus` names, or nil to leave
  loading alone.

  nil whenever narrowing could change which tests run:

  - No `--focus`. Nothing to narrow to.
  - Any `--focus-meta`. Metadata is only readable once a namespace is loaded,
    so a namespace cannot be ruled out before loading it.
  - Any focus value naming a suite or a suite alias, such as `--focus :unit`.
    Focus is an OR, so `--focus :unit --focus sepal.a-test` still runs all of
    `:unit`, and narrowing to `sepal.a-test` would silently drop the rest.

  A focus value that names neither a suite nor a real namespace narrows to
  nothing, and Kaocha reports that the focus matched no tests — which is what a
  typo deserves."
  [focus focus-meta suite-names]
  (when (and (seq focus) (empty? focus-meta))
    (let [suite-names (set suite-names)]
      (when-not (some suite-names focus)
        (->> focus
             (map #(or (namespace %) (name %)))
             distinct
             (mapv ns-pattern))))))

(defn- suite-names
  "Every name a focus value could be selecting a whole suite by."
  [config]
  (into #{}
        (mapcat (fn [suite]
                  (cons (:kaocha.testable/id suite)
                        (:kaocha.testable/aliases suite))))
        (:kaocha/tests config)))

(defn narrow-ns-patterns
  "Narrow every suite's :ns-patterns to the focused namespaces, or return the
  config untouched."
  [config]
  (if-let [patterns (ns-focus-patterns (:kaocha.filter/focus config)
                                       (:kaocha.filter/focus-meta config)
                                       (suite-names config))]
    (update config
            :kaocha/tests
            (partial mapv #(assoc % :kaocha/ns-patterns patterns)))
    config))

(def ^:private plugin
  "What kaocha.plugin/defplugin would have produced, written out.

  The macro emits exactly this map and the -register defmethod below, but
  clj-kondo does not know its binding forms and reports the hook body as two
  unresolved symbols. One hook is not worth a linter hook."
  {:kaocha.plugin/id :sepal.app.test/kaocha-focus
   :kaocha.plugin/description "Load only the namespaces --focus names."
   ;; Runs alongside kaocha.plugin/filter's own pre-load, which reads
   ;; :kaocha.filter/focus off the config the same way. Order between the two
   ;; does not matter: that hook sets :kaocha.testable/skip on suites and never
   ;; touches :ns-patterns. Both depend on the filter plugin's `config` step
   ;; having put :kaocha.filter/focus on the config from the CLI options.
   :kaocha.hooks/pre-load #'narrow-ns-patterns})

(defmethod plugin/-register :sepal.app.test/kaocha-focus [_ plugins]
  (conj plugins plugin))
