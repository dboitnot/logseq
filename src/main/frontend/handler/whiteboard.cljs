(ns frontend.handler.whiteboard
  (:require [clojure.string :as string]
            [datascript.core :as d]
            [frontend.db.model :as model]
            [frontend.db.utils :as db-utils]
            [frontend.modules.outliner.file :as outliner-file]
            [frontend.state :as state]
            [frontend.util :as util]
            [logseq.graph-parser.extract :as gp-extract]))

;; (defn set-linked-page-or-block!
;;   [page-or-block-id]
;;   (when-let [app ^js (state/get-current-whiteboard)]
;;     (let [shapes (:whiteboard/linked-shapes @state/state)]
;;       (when (and (seq shapes) page-or-block-id)
;;         (let [fs (first shapes)]
;;           (.updateShapes app (clj->js
;;                               [{:id (.-id fs)
;;                                 :logseqLink page-or-block-id}])))))))


;; (defn inside-whiteboard?
;;   [el]
;;   (println el)
;;   (loop [el el]
;;     (cond (nil? el) false
;;           (and (.-classList el) (.. el -classList (contains "whiteboard"))) true
;;           :else (recur (.-parentElement el)))))

;; FIXME: embed /draw should be supported too
;; FIXME: should use current target to see if it is actually inside of whiteboard
(defn whiteboard-mode?
  []
  (= (state/get-current-route) :whiteboard))

(defn get-tldr-app
  []
  js/window.tln)

(defn get-tldr-api
  []
  (when (get-tldr-app) js/tln.api))

(defn create-page!
  [page-title]
  (when-let [app (get-tldr-app)]
    (when-not (string/blank? page-title)
      (.createShapes app (clj->js
                          [{:id (str "logseq-portal-" page-title)
                            :type "logseq-portal"
                            :pageId page-title}])))))

(defn- block->shape [block]
  (:block/properties block))

(defn- shape->block [shape page-name]
  (let [properties (assoc shape :ls-type :whiteboard-shape)
        block {:block/page {:block/name (util/page-name-sanity-lc page-name)}
               :block/parent {:block/name page-name}
               :block/properties properties}
        additional-props (gp-extract/with-whiteboard-block-props block)]
    (merge block additional-props)))

(defn- tldr-page->blocks-tx [page-name tldr-data]
  (let [original-page-name page-name
        page-name (util/page-name-sanity-lc page-name)
        page-block {:block/name page-name
                    :block/original-name original-page-name
                    :block/whiteboard? true
                    :block/properties (dissoc tldr-data :shapes)}
        existing-blocks (model/get-page-blocks-no-cache page-name)
        blocks (mapv #(shape->block % page-name) (:shapes tldr-data))
        block-ids (set (map :block/uuid blocks))
        delete-shapes (filter (fn [shape]
                                (not (block-ids (:block/uuid shape))))
                              existing-blocks)
        delete-shapes-tx (mapv (fn [s] [:db/retractEntity (:db/id s)]) delete-shapes)]
    (concat [page-block] blocks delete-shapes-tx)))

(defn- get-whiteboard-clj [page-name]
  (when (model/page-exists? page-name)
    (let [page-block (model/get-page page-name)
          blocks (model/get-page-blocks-no-cache page-name)]
      [page-block blocks])))

(defn- whiteboard-clj->tldr [page-block blocks shape-id]
  (let [id (str (:block/uuid page-block))
        shapes (map block->shape blocks)
        page-properties (:block/properties page-block)
        assets (:assets page-properties)
        page-properties (dissoc page-properties :assets)]
    (clj->js {:currentPageId id
              :assets (or assets #js[])
              :selectedIds (if (not-empty shape-id) #js[shape-id] #js[])
              :pages [(merge page-properties
                             {:id id
                              :name "page"
                              :shapes shapes})]})))

(defn transact-tldr! [page-name tldr]
  (let [{:keys [pages assets]} (js->clj tldr :keywordize-keys true)
        page (first pages)
        tx (tldr-page->blocks-tx page-name (assoc page :assets assets))]
    (db-utils/transact! tx)))

(defn get-default-tldr
  [page-id]
  #js {:currentPageId page-id,
       :selectedIds #js [],
       :pages #js [#js {:id page-id,
                        :name "Page",
                        :shapes #js [],
                        :bindings #js {},
                        :nonce 1}],
       :assets #js []})

(defn get-whiteboard-entity [page-name]
  (db-utils/entity [:block/name (util/page-name-sanity-lc page-name)]))

(defn create-new-whiteboard-page!
  [name]
  (let [uuid (or (parse-uuid name) (d/squuid))
        tldr (get-default-tldr (str uuid))]
    (transact-tldr! name (get-default-tldr (str uuid)))
    (let [entity (get-whiteboard-entity name)
          tx (assoc (select-keys entity [:db/id])
                    :block/uuid uuid)]
      (db-utils/transact! [tx])
      (let [page-entity (get-whiteboard-entity name)]
        (when (and page-entity (nil? (:block/file page-entity)))
          (outliner-file/sync-to-file page-entity))))
    tldr))

(defn page-name->tldr!
  ([page-name]
   (page-name->tldr! page-name nil))
  ([page-name shape-id]
   (if-let [[page-block blocks] (get-whiteboard-clj page-name)]
     (whiteboard-clj->tldr page-block blocks shape-id)
     (create-new-whiteboard-page! page-name))))

(defn ->logseq-portal-shape
  [block-id point]
  {:blockType "B"
   :id (str (d/squuid))
   :pageId (str block-id)
   :point point
   :size [600, 400]
   :type "logseq-portal"})

(defn add-new-block-shape!
  [block-id client-x client-y]
  (let [api (get-tldr-api)
        point (js->clj (.. (get-tldr-app) -viewport (getPagePoint #js[client-x client-y])))
        shape (->logseq-portal-shape block-id point)]
    (.createShapes api (clj->js shape))))