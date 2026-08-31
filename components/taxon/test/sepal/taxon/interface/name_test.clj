(ns sepal.taxon.interface.name-test
  "Botanical typography is correctness, not decoration. A botanist judges
   whether the software knows the domain by whether the names are set right.

   Counts below are from the WFO reference taxonomy loaded in development:
   453,167 taxa, of which 6,011 carry a hybrid marker, 26,214 ` subsp. `,
   25,919 ` var. ` and 756 ` f. `."
  (:require [clojure.test :refer [deftest is testing]]
            [sepal.taxon.interface.name :as taxon.name]))

(deftest test-plain-binomial
  (is (= [{:text "Quercus alba" :role :scientific}]
         (taxon.name/segments "Quercus alba"))))

(deftest test-hybrid-marker-stays-upright
  (testing "6,011 reference names carry a × and it is never italic"
    (is (= [{:text "Nepenthes" :role :scientific}
            {:text " × " :role :upright}
            {:text "hookeriana" :role :scientific}]
           (taxon.name/segments "Nepenthes × hookeriana")))))

(deftest test-connecting-terms-stay-upright
  (testing "subsp. — 26,214 rows"
    (is (= [{:text "Eleocharis mamillata" :role :scientific}
            {:text " subsp. " :role :upright}
            {:text "austriaca" :role :scientific}]
           (taxon.name/segments "Eleocharis mamillata subsp. austriaca"))))
  (testing "var. — 25,919 rows"
    (is (= [{:text "Cyperus pangorei" :role :scientific}
            {:text " var. " :role :upright}
            {:text "ambiguus" :role :scientific}]
           (taxon.name/segments "Cyperus pangorei var. ambiguus"))))
  (testing "f. — 756 rows"
    (is (= [{:text "Carex kitaibeliana" :role :scientific}
            {:text " f. " :role :upright}
            {:text "balcanica" :role :scientific}]
           (taxon.name/segments "Carex kitaibeliana f. balcanica")))))

(deftest test-autonym-repeats-the-epithet
  (testing "Carex fusiformis subsp. fusiformis is a real and common shape"
    (is (= [{:text "Carex fusiformis" :role :scientific}
            {:text " subsp. " :role :upright}
            {:text "fusiformis" :role :scientific}]
           (taxon.name/segments "Carex fusiformis subsp. fusiformis")))))

(deftest test-cultivar-epithet-stays-upright
  (testing "ICNCP sets the epithet upright inside single quotes"
    (is (= [{:text "Acer palmatum " :role :scientific}
            {:text "'Sango-kaku'" :role :upright}]
           (taxon.name/segments "Acer palmatum 'Sango-kaku'")))))

(deftest test-cultivar-epithet-containing-an-apostrophe
  (testing "'Nuccio's Pearl' is a real cultivar — greedy quote pairing splits it
            after Nuccio and italicises the rest of the epithet"
    (is (= [{:text "Camellia japonica " :role :scientific}
            {:text "'Nuccio's Pearl'" :role :upright}]
           (taxon.name/segments "Camellia japonica 'Nuccio's Pearl'")))))

(deftest test-genus-plus-cultivar-with-no-species
  (is (= [{:text "Hosta " :role :scientific}
          {:text "'Sum and Substance'" :role :upright}]
         (taxon.name/segments "Hosta 'Sum and Substance'"))))

(deftest test-hybrid-and-cultivar-together
  (is (= [{:text "Nepenthes" :role :scientific}
          {:text " × " :role :upright}
          {:text "hookeriana " :role :scientific}
          {:text "'Chelsea'" :role :upright}]
         (taxon.name/segments "Nepenthes × hookeriana 'Chelsea'"))))

(deftest test-blank-and-nil
  (is (= [] (taxon.name/segments nil)))
  (is (= [] (taxon.name/segments "")))
  (is (= [] (taxon.name/segments "   "))))

(deftest test-a-lone-apostrophe-is-not-a-cultivar
  (testing "an unpaired quote must not swallow the name"
    (is (= [{:text "Quercus alba'" :role :scientific}]
           (taxon.name/segments "Quercus alba'")))))
