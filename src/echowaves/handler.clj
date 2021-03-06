(ns echowaves.handler
  (:require [compojure.route :as route]
            [compojure.core :refer [defroutes]]
            [noir.util.middleware :as noir-middleware]
            [echowaves.routes.auth :refer [auth-routes]]
            [echowaves.routes.home :refer [home-routes]]
            [echowaves.routes.upload :refer [upload-routes]]
            [echowaves.routes.wave :refer [wave-routes]]
            [echowaves.routes.blends :refer [blends-routes]]
            [noir.session :as session]
            [taoensso.timbre :as timbre]
            [com.postspectacular.rotor :as rotor]
            [ring.middleware.format :refer [wrap-restful-format]]
            [taoensso.timbre 
             :refer [trace debug info warn error fatal]]))

(defn info-appender [{:keys [level message]}]
  (println "level:" level "message:" message))

(defn init []
  (timbre/set-config!
   [:appenders :rotor]
   {:min-level :info
    :enabled? true
    :async? false ; should be always false for rotor
    :max-message-per-msecs nil
    :fn rotor/append})

  (timbre/set-config!
   [:shared-appender-config :rotor]
   {:path "logger.log" :max-size (* 512 1024) :backlog 10})
  (timbre/set-level! :info)
  (timbre/info "echowaves started successfully")
  )


(defn destroy []
  (timbre/info "echowaves is shutting down"))

(defroutes app-routes
  (route/resources "/")
  (route/not-found "Not Found"))

(defn wave-page [_]
  (session/get :wave))


(defn simple-logging-middleware [app]
  (fn [req]
    (debug req)
    ;; (info req)
    (app req)))


(def app (noir-middleware/app-handler
           [auth-routes
            home-routes
            upload-routes
            wave-routes
            blends-routes
            app-routes]
           :middleware [wrap-restful-format]
           :access-rules [wave-page]))

(def war-handler (noir-middleware/war-handler (simple-logging-middleware app)))
