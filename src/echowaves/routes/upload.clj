(ns echowaves.routes.upload  
  (:require [compojure.core :refer [defroutes GET POST]]
            [echowaves.views.layout :as layout]
            [hiccup.util :refer [url-encode]]
            [noir.io :refer [resource-path]]
            [noir.session :as session]
            [noir.response :as resp]
            [noir.util.route :refer [restricted]]            
            [clojure.java.io :as io]
            [echowaves.models.db :as db]
            [echowaves.util :refer [session-wave-path thumb-prefix]]
            [taoensso.timbre 
             :refer [trace debug info warn error fatal]])
  (:import [java.io File FileInputStream FileOutputStream]
           java.awt.image.BufferedImage
           java.awt.RenderingHints
           java.awt.geom.AffineTransform
           java.awt.image.AffineTransformOp
           javax.imageio.ImageIO))

(def thumb-size 600)

(defn scale [img ratio width height]  
  (let [scale        (AffineTransform/getScaleInstance 
                       (double ratio) (double ratio))
        transform-op (AffineTransformOp. 
                       scale AffineTransformOp/TYPE_BILINEAR)]    
    (.filter transform-op img (BufferedImage. width height (.getType img)))))

(defn scale-image [file]
  (let [img        (ImageIO/read file)
        img-width  (.getWidth img)
        img-height (.getHeight img)]
    (let [ratio (/ thumb-size img-height)]        
      (scale img ratio (int (* img-width ratio)) thumb-size))))

(defn save-thumbnail [{:keys [filename]}]
  (ImageIO/write 
    (scale-image (io/input-stream (str (session-wave-path) filename))) 
    "jpeg" 
    (File. (str (session-wave-path) thumb-prefix filename))))

(defn upload-page [params]
  (layout/render "upload.html" params))

(defn handle-upload [file]
  (info "uploading file:" file)
  (upload-page 
    (if (empty? (:filename file))
      {:error "please select a file to upload"}      
      (try 
        (noir.io/upload-file          
          (str File/separator "img" File/separator (session/get :wave) File/separator)
          file)
        (save-thumbnail file)
        (db/add-image (session/get :wave) (:filename file))
        {:image
         (str "/img/" (session/get :wave) "/" thumb-prefix (url-encode (:filename file)))}
        (catch Exception ex 
          {:error (str "error uploading file: " (.getMessage ex))})))))

(defn delete-image [wave_name name]
  (try
    (db/delete-image wave_name name)
    (io/delete-file (str (session-wave-path) name))
    (io/delete-file (str (session-wave-path) thumb-prefix name))
    "ok"
    (catch Exception ex
      (error ex "an error has occured while deleting" name)
      (.getMessage ex))))

(defn delete-images [names]
  (let [wave_name (session/get :wave)]
    (resp/edn
     (for [name names] {:name name :status (delete-image wave_name name)}))))

(defn handle-delete-image [name]
  (let [wave_name (session/get :wave)]
    (resp/json {:name name :status (delete-image wave_name name)})))

(defroutes upload-routes
  (GET "/upload" [info] (upload-page {:info info}))
  
  (POST "/upload" [file] 
        (restricted (handle-upload file)))
  
  (POST "/delete" [names] (restricted (delete-images names)))
  (POST "/delete-image.json" [name] (restricted (handle-delete-image name))))
