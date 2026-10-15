package com.aleph.graymatter.jtyposquatting.generator;

import com.aleph.graymatter.jtyposquatting.InvalidDomainException;
import com.aleph.graymatter.jtyposquatting.net.DomainName;

import java.util.List;

/**
 * Typo squatting generation algorithms, each bound to its implementation.
 */
public enum Algorithm {
    OMISSION("omission", CharacterEdits::addOmissions),
    REPETITION("repetition", CharacterEdits::addRepetitions),
    CHANGE_ORDER("changeOrder", CharacterEdits::addTranspositions),
    REPLACEMENT("replacement", Keyboard::addReplacements),
    DOUBLE_REPLACEMENT("doubleReplacement", Keyboard::addDoubleReplacements),
    ADDITION("addition", CharacterEdits::addAdditions),
    MISSING_DOT("missingDot", DotDash::addMissingDots),
    STRIP_DASH("stripDash", Dash::removeDash),
    VOWEL_SWAP("vowelSwap", CharacterEdits::addVowelSwaps),
    ADD_DASH("addDash", Dash::addDash),
    HOMOGLYPH("homoglyph", Homoglyph::addHomglyphedDomains),
    COMMON_MISSPELLING("commonMisspelling", Misspell::AddMisspelledDomains),
    HOMOPHONES("homophones", Words::addHomophones),
    WRONG_TLD("wrongTld", TLD::AddAndReplaceAllTLD),
    ADD_TLD("addTld", Suffixes::addTld),
    SUBDOMAIN("subdomain", DotDash::addSubdomains),
    SINGULAR_PLURALIZE("singularPluralize", Words::addSingularPlural),
    CHANGE_DOT_DASH("changeDotDash", DotDash::addDotToDash),
    WRONG_SLD("wrongSld", Suffixes::addWrongSld),
    NUMERAL_SWAP("numeralSwap", Words::addNumeralSwaps),
    ADD_DYNAMIC_DNS("addDynamicDns", Suffixes::addDynamicDns);

    @FunctionalInterface
    interface Generator {
        void generate(DomainName domainName, List<DomainName> resultList) throws InvalidDomainException;
    }

    private final String id;
    private final Generator generator;

    Algorithm(String id, Generator generator) {
        this.id = id;
        this.generator = generator;
    }

    public String getId() {
        return id;
    }

    public void generate(DomainName domainName, List<DomainName> resultList) throws InvalidDomainException {
        generator.generate(domainName, resultList);
    }
}
