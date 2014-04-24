(defproject group-redis "0.6.0-SNAPSHOT"
  :description "Group management api that supports locks, empheral data and membership join and leave notification"
  :url "https://github.com/gerritjvv/group-redis"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

 :javac-options ["-target" "1.6" "-source" "1.6" "-Xlint:-options"] 
 :warn-on-reflection true
  
 :java-source-paths ["java" "test_java"]
 :junit ["test_java"]
  
 :plugins [
         [lein-rpm "0.0.5"] [lein-midje "3.0.1"] [lein-marginalia "0.7.1"]
         [lein-kibit "0.0.8"] [no-man-is-an-island/lein-eclipse "2.0.0"]
	 [lein-junit "1.1.2"]
           ]

  :dependencies [
     [org.clojure/core.async "0.1.267.0-0d7780-alpha"]
     [org.clojure/test.check "0.5.7"]
     [com.taoensso/carmine "2.4.4"]
     [org.clojure/tools.logging "0.2.3"]            
     [fun-utils "0.4.0"]
		 [midje "1.6-alpha2" :scope "test"]
     [junit/junit "4.11" :scope "test"]
		 [org.clojure/clojure "1.6.0" :scope "provided"]])
