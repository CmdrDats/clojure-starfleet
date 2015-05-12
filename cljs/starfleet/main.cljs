(ns starfleet.main
  (:require
    [om.core :as om :include-macros true]
    [om.dom :as dom :include-macros true]
    [kioo.om :as ko]
    [kioo.core :as k]
    [cljs.core.async :as async :refer (<! >! put! chan)]
    [taoensso.sente  :as sente :refer (cb-success?)])
  (:require-macros
    [cljs.core.async.macros :as asyncm :refer (go go-loop)]
    [kioo.om :refer [defsnippet deftemplate] ]))


(defonce chan-socket (atom nil))

(def state (atom {:entries ["entry 1" "Another one" "yet another"]}))

(defsnippet
  entry "html/template.html" [:.entry] [text]
  {[:.entry] (ko/content text)})



(defn rootpane [data owner]
   (om/component (apply dom/div nil (map entry (:entries data)))))

(om/root
  rootpane
  state
  {:target (.getElementById js/document "app")}
  )

(defn handle-message [{:keys [id ?data event] :as socket}]
  (.log js/console (str id "--" ?data)))


(defn startup []
  (let [socket (sente/make-channel-socket! "/chsk" {:type :auto})]
    (reset! chan-socket socket)
    (sente/start-chsk-router! (:ch-recv socket) handle-message)
    (js/setInterval #((:send-fn socket) [:starfleet/beamup {:time "now"}]) 2000))
  true)

(defonce s (atom (startup)))

