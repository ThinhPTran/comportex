(ns org.nfrac.comportex.demos.cortical-io-channel
  (:require [org.nfrac.comportex.core :as core]
            [org.nfrac.comportex.protocols :as p]
            [org.nfrac.comportex.cortical-io :refer [cortical-io-encoder
                                                     cache-fingerprint!]]
            [clojure.string :as str]
            [clojure.core.async :as async
             :refer [chan <!! >!! thread close!]]))

(def spec
  {:column-dimensions [40 40]
   :ff-init-frac 0.15
   :ff-potential-radius 1.0
   :proximal {:stimulus-threshold 3}
   :global-inhibition? false
   :activation-level 0.015
   :distal-vs-proximal-weight 1.0
   })

(def higher-level-spec
  (merge spec
         {:column-dimensions [20 20]
          :proximal {:max-segments 5}}))

(defn split-sentences
  [text]
  (->> (str/split (str/trim text) #"[^\w]*[\.\!\?]+[^\w]*")
       (mapv #(str/split % #"[^\w']+"))
       ;(mapv #(conj % "."))
       ))

(defn n-region-model
  [api-key cache n]
  (core/regions-in-series n core/sensory-region
                          (list* spec (repeat higher-level-spec))
                          {:input (cortical-io-encoder api-key cache)}))

(comment

  ;; interactive session feeding in text and requesting predictions

  (def api-key blah)

  ;; allow http requests to go in parallel to model running
  (def preload-c (chan 1000))
  (def input-c (chan 5))
  (def cache (atom {}))
  ;; the HTM model
  (def current (atom (n-region-model api-key cache 1)))

  (defn submit
    [txt]
    (let [words (apply concat (split-sentences txt))]
      (doseq [word words]
        (>!! preload-c word))))

  (def preloader
    (thread
      (try
        (loop []
          (when-let [term (<!! preload-c)]
            (println term)
            (cache-fingerprint! api-key cache term)
            (>!! input-c term)
            (recur)))
        (catch Exception e
          (println e)))))

  (def stepper
    (thread
      (try
        (loop []
          (let [in-val (<!! input-c)]
            (swap! current p/htm-step in-val)
            (recur)))
        (catch Exception e
          (println e)))))

  (defn predict
    [n]
    (println (map :value (core/predictions @current n))))

  (submit "greetings earth, greetings earth, greetings earth, greetings earth")
  (submit "neural networks, neural networks, neural networks, neural networks")
  (predict 3)

  (defn stream-of-associations
    [n]
    (loop [i n]
      (when (pos? i)
       (let [p-word (:value (first (core/predictions @current 1)))]
         (when p-word
           (submit p-word)
           (Thread/sleep 1000)
           (recur (dec i)))))))

  (stream-of-associations 5)

  (close! preload-c)
  (close! input-c)
  (close! preloader)
  (close! stepper)

  )
