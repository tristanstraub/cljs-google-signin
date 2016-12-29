(ns cljs-google-signin.component
  (:require-macros [cljs.core.async.macros :as a])
  (:require [cljs.core.async :as a]
            [rum.core :as r]
            [goog.dom :as dom]))

(defn- <cb
  "Call an callback style function and return a channel containing the result of calling the callback"
  [f & args]
  (let [out (a/chan)]
    (apply f (conj (into [] args) #(a/close! out)))
    out))

(defn- <promise
  "Call a function that returns a promise and convert it into a channel"
  [f & args]
  (let [out  (a/chan)
        done (fn [& _] (a/close! out))]

    (.then (apply f args) done done)

    out))

(defn- render-signin-button
  "Get gapi to render sign-in button within the provided container element"
  [el & {:keys [on-success on-failure]}]
  (js/gapi.signin2.render el
                          #js {"scope"     "profile email"
                               "width"     240
                               "height"    50
                               "longtitle" true
                               "theme"     "dark"
                               "onsuccess" on-success
                               "onfailure" on-failure}))

(defn- get-dom-node
  "Get a rum component dom element"
  [state]
  (-> state :rum/react-component js/ReactDOM.findDOMNode))

(defn- <load-script [url]
  (a/go (let [s (dom/createElement "script")]
          (set! (.-src s) url)
          (let [loaded (<cb (fn [cb] (set! (.-onload s) cb)))]
            (.appendChild (.-body js/document) s)
            (a/<! loaded)))))

(defn- <init-gapi!
  "Initialise gapi library"
  [client-id]
  (assert client-id)
  (a/go
    (a/<! (<cb js/gapi.load "auth2"))
    (a/<! (<promise js/gapi.auth2.init #js {:client_id client-id}))))

(defn- <ensure-gapi!
  "Ensure that the gapi script is loaded and initialised"
  [client-id]
  (a/go
    (when-not (exists? js/gapi)
      (a/<! (<load-script "https://apis.google.com/js/platform.js")))
    (a/<! (<init-gapi! client-id))))

(def ^:private button
  "On mount, render a google sign-in button"
  {:did-mount (fn [state]
                (let [el (get-dom-node state)]
                  (a/go
                    (let [[& {:keys [client-id on-success on-failure]}] (:rum/args state)]
                      (a/<! (<ensure-gapi! client-id))
                      (render-signin-button el
                                            :on-success on-success
                                            :on-failure on-failure))))
                state)})

(defn <sign-out!
  "Sign user out of gapi session"
  []
  (<promise #(.signOut (js/gapi.auth2.getAuthInstance))))

(r/defc signin-button < button [& {:keys [client-id on-success on-failure]}]
  [:div])
