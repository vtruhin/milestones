(ns milestones.nlp-tools
  (:require [milestones.parser-rules :refer [rules item-significant-value?]]))

(def nlp (.-nlp_compromise js/window))

(def lexicon (.lexicon nlp))
(aset lexicon "task" "Task")
(aset lexicon "tasks" "Task")
(aset lexicon "milestone" "Milestone")
(aset lexicon "milestones" "Milestone")

(defn pos-tags-lexicon
  [lexicon
   sentence]
  (let [nlp-sentence (.sentence nlp sentence #js {:lexicon lexicon})
        nlp-terms (.-terms nlp-sentence)]
    (map
     (fn [term] [(js->clj  (.-text term))
                 (js->clj   (.-pos term) :keywordize-keys true )])
     nlp-terms)))

(def pos-tags (partial pos-tags-lexicon lexicon))


;; A stack for a recursive descent parser, containing what keys to accept at every  stage:
;; '( ...       #{:noun :verb}  ...        :a-step)
;;  at this stage --^  must be found -----------^ or this is a step to do something






(defn accept-tag
  "Verifies if an input like: [\"task\" {:Noun true}] correponds to
  one of the keys stored in the head of tag-stack, or if it is a
  checkpoint, to notify the caller to construct a part of the task."
  [input-item
   tag-stack]
  ;; pick the tag that should be there in the stack

  (if-let [current-tag-alternatives (first tag-stack)] ;; #{[:noun :verb ]...}
    (cond
      (keyword? current-tag-alternatives) {:step current-tag-alternatives
                                           :new-stack (rest tag-stack)} 
      ;; It corrsponds to one of the alternatives
      (some #{(as-> input-item i
                (get i 1)
                (set (keys i)))} current-tag-alternatives) {:step false
                                                      :new-stack (rest tag-stack)}
      :default false)
    false))

(defn fast-forward
  "Goes FFW in a tag-stack until it finds a step specification. "
  [tag-stack]
  (if (seq tag-stack)
    (if (keyword? (first tag-stack))
      tag-stack
      (recur (rest tag-stack)))
    '()))

(defn parse-task-w-a-tag-stack
  [task-str
   init-tag-stack
   optional-steps]
  (loop
      [input-items (->> task-str
                        pos-tags
                        (filter (comp not empty? #(get % 1))))
       tag-stack init-tag-stack
       output-stack {}
       output {}]
    (if  (seq input-items)
      (let [input-item (first input-items)]
        (if-let [{:keys [step new-stack]}
                 (accept-tag input-item tag-stack)]
          ;; parse if successful, let's see:
          (cond
            step (recur input-items
                        new-stack
                        {:step step :items []}
                        (if  (empty? (get output-stack :items))
                          output
                           (assoc output (get output-stack :step)
                                 (get  output-stack :items))))
            :default (recur (rest input-items)
                            new-stack
                            (if (item-significant-value? input-item (get output-stack :step))
                              (merge-with conj output-stack {:items (get  input-item 0)})
                              output-stack) 
                            output))
          
          (if (some #{(get output-stack  :step)} optional-steps)
            (recur input-items
                   (fast-forward  tag-stack)
                   output-stack
                   output)
            {:error {:step (get output-stack  :step) :expected (first tag-stack) :item input-item}})))
      (if  (empty? tag-stack)
        
        {:error false  :result  (assoc output (get output-stack :step)
                                       (get  output-stack :items))}
        {:error {:step (get output-stack  :step) :expected (first tag-stack) :item nil }}))))



(defn curate-items
  [item-vector]
  
  )