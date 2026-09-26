(ns sepal.i18n.plural
  "Compiles the C expression in a PO file's Plural-Forms header into a function
  from a count to a form index.

  The grammar is the subset gettext documents for plural expressions: `n`,
  integers, parentheses, the ternary, `|| && == != < > <= >=`, and
  `+ - * / %`."
  (:require [clojure.string :as str]))

(defn- fail [expr msg]
  (throw (ex-info (str "Bad Plural-Forms expression " (pr-str expr) ": " msg)
                  {:expr expr})))

(defn- tokenize [expr]
  (map (fn [t] (if (re-matches #"\d+" t) (parse-long t) t))
       (re-seq #"\d+|n|\|\||&&|==|!=|<=|>=|[<>?:()+\-*/%!]|\S" expr)))

(def ^:private binary-ops
  ;; operator -> [precedence f]
  {"||" [1 (fn [a b] (if (or (not= 0 a) (not= 0 b)) 1 0))]
   "&&" [2 (fn [a b] (if (and (not= 0 a) (not= 0 b)) 1 0))]
   "==" [3 (fn [a b] (if (= a b) 1 0))]
   "!=" [3 (fn [a b] (if (not= a b) 1 0))]
   "<" [4 (fn [a b] (if (< a b) 1 0))]
   ">" [4 (fn [a b] (if (> a b) 1 0))]
   "<=" [4 (fn [a b] (if (<= a b) 1 0))]
   ">=" [4 (fn [a b] (if (>= a b) 1 0))]
   "+" [5 +]
   "-" [5 -]
   "*" [6 *]
   "/" [6 quot]
   "%" [6 rem]})

(declare parse-ternary)

(defn- parse-primary [expr [t & more]]
  (cond
    (= t "n") [(fn [n] n) more]
    (int? t) [(constantly t) more]
    (= t "!") (let [[f more] (parse-primary expr more)]
                [(fn [n] (if (= 0 (f n)) 1 0)) more])
    (= t "(") (let [[f [close & more]] (parse-ternary expr more)]
                (when (not= close ")") (fail expr "missing )"))
                [f more])
    :else (fail expr (str "unexpected " (pr-str t)))))

(defn- parse-binary [expr tokens min-prec]
  (loop [[lhs tokens] (parse-primary expr tokens)]
    (let [[prec op] (get binary-ops (first tokens))]
      (if (and prec (>= prec min-prec))
        (let [[rhs more] (parse-binary expr (rest tokens) (inc prec))]
          (recur [(fn [n] (op (lhs n) (rhs n))) more]))
        [lhs tokens]))))

(defn- parse-ternary [expr tokens]
  (let [[test more] (parse-binary expr tokens 1)]
    (if (= "?" (first more))
      (let [[then [colon & more]] (parse-ternary expr (rest more))]
        (when (not= colon ":") (fail expr "missing :"))
        (let [[else more] (parse-ternary expr more)]
          [(fn [n] (if (not= 0 (test n)) (then n) (else n))) more]))
      [test more])))

(defn parse
  "Parse a Plural-Forms value such as `nplurals=2; plural=(n != 1);` into
  {:nplurals 2 :index (fn [n] ...)}. :index is clamped to [0, nplurals)."
  [header-value]
  (let [nplurals (some->> header-value (re-find #"nplurals\s*=\s*(\d+)") second parse-long)
        expr (some->> header-value (re-find #"plural\s*=\s*([^;]+)") second str/trim)]
    (when-not (and nplurals expr)
      (fail header-value "needs nplurals= and plural="))
    (let [[f more] (parse-ternary expr (tokenize expr))
          top (dec nplurals)]
      (when (seq more) (fail expr (str "unexpected " (pr-str (first more)))))
      {:nplurals nplurals
       :index (fn [n] (-> (f (long n)) (max 0) (min top)))})))
