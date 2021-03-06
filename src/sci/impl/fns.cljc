(ns sci.impl.fns
  {:no-doc true}
  (:require [sci.impl.types :as t]))

(defn throw-arity [fn-name macro? args]
  (throw (new #?(:clj Exception
                 :cljs js/Error)
              (let [actual-count (if macro? (- (count args) 2)
                                     (count args))]
                (str "Cannot call " fn-name " with " actual-count " arguments")))))

(deftype Recur #?(:clj [val]
                  :cljs [val])
  t/IBox
  (getVal [this] val))

(defn parse-fn-args+body
  [ctx interpret eval-do*
   {:sci.impl/keys [fixed-arity var-arg-name params body] :as _m}
   fn-name macro?]
  (let [min-var-args-arity (when var-arg-name fixed-arity)
        m (if min-var-args-arity
            {:sci.impl/min-var-args-arity min-var-args-arity}
            {:sci.impl/fixed-arity fixed-arity})]
    (with-meta
      (fn run-fn [& args]
        (let [;; tried making bindings a transient, but saw no perf improvement (see #246)
              bindings (:bindings ctx)
              bindings
              (loop [args (seq args)
                     params (seq params)
                     ret bindings]
                (if params
                  (let [fp (first params)]
                    (if (= '& fp)
                      (assoc ret (second params) args)
                      (do
                        (when-not args
                          (throw-arity fn-name macro? args))
                        (recur (next args) (next params)
                               (assoc ret fp (first args))))))
                  (do
                    (when args
                      (throw-arity fn-name macro? args))
                    ret)))
              ctx (assoc ctx :bindings bindings)
              ret (if (= 1 (count body))
                    (interpret ctx (first body))
                    (eval-do* ctx body))
              ;; m (meta ret)
              recur? (instance? Recur ret)]
          (if recur? (recur (t/getVal ret)) ret)))
      m)))

(defn lookup-by-arity [arities arity]
  (some (fn [f]
          (let [{:sci.impl/keys [fixed-arity min-var-args-arity]} (meta f)]
            (when (or (= arity fixed-arity )
                      (and min-var-args-arity
                           (>= arity min-var-args-arity)))
              f))) arities))

(defn eval-fn [ctx interpret eval-do* {:sci.impl/keys [fn-bodies fn-name] :as f}]
  (let [macro? (:sci/macro f)
        self-ref (atom nil)
        call-self (fn [& args]
                    (apply @self-ref args))
        ctx (if fn-name (assoc-in ctx [:bindings fn-name] call-self)
                ctx)
        single-arity? (= 1 (count fn-bodies))
        arities (map #(parse-fn-args+body ctx interpret eval-do* % fn-name macro?) fn-bodies)
        f (vary-meta
           (if single-arity?
             (first arities)
             (fn [& args]
               (let [arg-count (count args)]
                 (if-let [f (lookup-by-arity arities arg-count)]
                   (apply f args)
                   (throw (new #?(:clj Exception
                                  :cljs js/Error)
                               (let [actual-count (if macro? (- arg-count 2)
                                                      arg-count)]
                                 (str "Cannot call " fn-name " with " actual-count " arguments"))))))))
           #(assoc % :sci/macro macro?))]
    (reset! self-ref f)
    f))

;;;; Scratch

(comment
  )
