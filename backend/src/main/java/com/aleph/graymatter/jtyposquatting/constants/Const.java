package com.aleph.graymatter.jtyposquatting.constants;

import java.util.List;
import java.util.Map;
import static java.util.Map.entry;

public final class Const {

    // Replacement of String[] with immutable List<String>
    public static final List<String> ALGO_NAME_LIST = List.of(
            "omission", "repetition", "changeOrder", "replacement", "doubleReplacement",
            "addition", "missingDot", "stripDash", "vowelSwap", "addDash", "homoglyph",
            "commonMisspelling", "homophones", "wrongTld", "addTld", "subdomain",
            "singularPluralize", "changeDotDash", "wrongSld", "numeralSwap", "addDynamicDns"
    );

    public static final List<String> EXCLUDED_TLD = List.of("gouv.fr"); // for example

    // An immutable Map defined in a single clear block (no need for A to Z variables!)
    public static final Map<Character, List<String>> SIMILAR_CHAR = Map.ofEntries(
            entry('0', List.of("o")),
            entry('1', List.of("l", "i", "ı")),
            entry('2', List.of("ƻ")),
            entry('5', List.of("ƽ")),
            entry('a', List.of("à", "á", "à", "â", "ã", "ä", "å", "ɑ", "ạ", "ǎ", "ă", "ȧ", "ą", "ə")),
            entry('b', List.of("d", "ʙ", "ɓ", "ḃ", "ḅ", "ḇ", "ƅ")),
            entry('c', List.of("e", "ƈ", "ċ", "ć", "ç", "č", "ĉ", "ᴄ")),
            entry('d', List.of("b", "cl", "ɗ", "đ", "ď", "ɖ", "ḑ", "ḋ", "ḍ", "ḏ", "ḓ")),
            entry('e', List.of("c", "é", "è", "ê", "ë", "ē", "ĕ", "ě", "ė", "ẹ", "ę", "ȩ", "ɇ", "ḛ")),
            entry('f', List.of("ƒ", "ḟ")),
            entry('g', List.of("q", "ɢ", "ɡ", "ġ", "ğ", "ǵ", "ģ", "ĝ", "ǧ", "ǥ")),
            entry('h', List.of("ĥ", "ȟ", "ħ", "ɦ", "ḧ", "ḩ", "ⱨ", "ḣ", "ḥ", "ḫ", "ẖ")),
            entry('i', List.of("1", "l", "í", "ì", "ï", "ı", "ɩ", "ǐ", "ĭ", "ỉ", "ị", "ɨ", "ȋ", "ī", "ɪ")),
            entry('j', List.of("ʝ", "ǰ", "ɉ", "ĵ")),
            entry('k', List.of("lc", "ḳ", "ḵ", "ⱪ", "ķ", "ᴋ")),
            entry('l', List.of("1", "i", "ɫ", "ł", "ı", "ɩ")),
            entry('m', List.of("n", "nn", "rn", "rr", "ṁ", "ṃ", "ᴍ", "ɱ", "ḿ")),
            entry('n', List.of("m", "r", "ń", "ṅ", "ṇ", "ṉ", "ñ", "ņ", "ǹ", "ň", "ꞑ")),
            entry('o', List.of("0", "ȯ", "ọ", "ỏ", "ơ", "ó", "ö", "ᴏ")),
            entry('p', List.of("ƿ", "ƥ", "ṕ", "ṗ")),
            entry('q', List.of("g", "ʠ")),
            entry('r', List.of("ʀ", "ɼ", "ɽ", "ŕ", "ŗ", "ř", "ɍ", "ɾ", "ȓ", "ȑ", "ṙ", "ṛ", "ṟ")),
            entry('s', List.of("ʂ", "ś", "ṣ", "ṡ", "ș", "ŝ", "š", "ꜱ")),
            entry('t', List.of("ţ", "ŧ", "ṫ", "ṭ", "ț", "ƫ")),
            entry('u', List.of("ᴜ", "ǔ", "ŭ", "ü", "ʉ", "ù", "ú", "û", "ũ", "ū", "ų", "ư", "ů", "ű", "ȕ", "ȗ", "ụ")),
            entry('v', List.of("ṿ", "ⱱ", "ᶌ", "ṽ", "ⱴ", "ᴠ")),
            entry('w', List.of("vv", "ŵ", "ẁ", "ẃ", "ẅ", "ⱳ", "ẇ", "ẉ", "ẘ", "ᴡ")),
            entry('x', List.of("ẋ", "ẍ")),
            entry('y', List.of("ʏ", "ý", "ÿ", "ŷ", "ƴ", "ȳ", "ɏ", "ỿ", "ẏ", "ỵ")),
            entry('z', List.of("ʐ", "ż", "ź", "ᴢ", "ƶ", "ẓ", "ẕ", "ⱬ"))
    );

    public static final Map<Character, List<String>> NUMERAL = Map.ofEntries(
            entry('0', List.of("zero")),
            entry('1', List.of("one", "first")),
            entry('2', List.of("two", "second")),
            entry('3', List.of("three", "third")),
            entry('4', List.of("four", "fourth", "for")),
            entry('5', List.of("five", "fifth")),
            entry('6', List.of("six", "sixth")),
            entry('7', List.of("seven", "seventh")),
            entry('8', List.of("eight", "eighth")),
            entry('9', List.of("nine", "ninth"))
    );

    private Const() {
        // Private constructor to prevent instantiation of this utility class
    }
}