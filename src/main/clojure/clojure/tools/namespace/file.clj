;; Copyright (c) Stuart Sierra, 2012. All rights reserved. The use and
;; distribution terms for this software are covered by the Eclipse
;; Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;; which can be found in the file epl-v10.html at the root of this
;; distribution. By using this software in any fashion, you are
;; agreeing to be bound by the terms of this license. You must not
;; remove this notice, or any other, from this software.

(ns ^{:author "Stuart Sierra"
      :doc "Read and track namespace information from files"}
  clojure.tools.namespace.file
  (:require [clojure.java.io :as io]
            [clojure.tools.namespace.parse :as parse]
            [clojure.tools.namespace.track :as track])
  (:import (java.io PushbackReader)))

(defn read-file-ns-decl
  "Attempts to read a (ns ...) declaration from file, and returns the
  unevaluated form. Returns nil if read fails due to invalid syntax or
  if a ns declaration cannot be found. read-opts is passed through to
  clojure.core/read if this version of Clojure supports it."
  ([file]
   (read-file-ns-decl file nil))
  ([file read-opts]
   (with-open [rdr (PushbackReader. (io/reader file))]
     (parse/read-ns-decl rdr read-opts))))

(defn clojure-file?
  "Returns true if the java.io.File represents a file which will be
  read by the Clojure (JVM) compiler."
  [^java.io.File file]
  (and (.isFile file)
       (or
         (.endsWith (.getName file) ".clj")
         (.endsWith (.getName file) ".cljc"))))

(defn clojurescript-file?
  "Returns true if the java.io.File represents a file which will be
  read by the ClojureScript compiler."
  {:added "0.3.0"}
  [^java.io.File file]
  (and (.isFile file)
       (or
         (.endsWith (.getName file) ".cljs")
         (.endsWith (.getName file) ".cljc"))))

;;; Dependency tracker

(defn- files-and-deps [files]
  (reduce (fn [m file]
            (if-let [decl (read-file-ns-decl file)]
              (let [deps (parse/deps-from-ns-decl decl)
                    name (second decl)]
                (-> m
                    (assoc-in [:depmap name] deps)
                    (assoc-in [:filemap file] name)))
              m))
          {} files))

(def ^:private merge-map (fnil merge {}))

(defn add-files
  "Reads ns declarations from files; returns an updated dependency
  tracker with those files added."
  [tracker files]
  (let [{:keys [depmap filemap]} (files-and-deps files)]
    (-> tracker
        (track/add depmap)
        (update-in [::filemap] merge-map filemap))))

(defn remove-files
  "Returns an updated dependency tracker with files removed. The files
  must have been previously added with add-files."
  [tracker files]
  (-> tracker
      (track/remove (keep (::filemap tracker {}) files))
      (update-in [::filemap] #(apply dissoc % files))))

