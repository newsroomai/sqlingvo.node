(ns sqlingvo.node.test
  (:require [clojure.spec.test.alpha :as stest]
            [doo.runner :refer-macros [doo-tests]]
            [sqlingvo.node.async-test]))

(stest/instrument)

(doo-tests 'sqlingvo.node.async-test)
