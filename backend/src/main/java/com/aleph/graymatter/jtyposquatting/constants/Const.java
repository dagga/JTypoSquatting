package com.aleph.graymatter.jtyposquatting.constants;

import java.util.List;
import java.util.Map;
import static java.util.Map.entry;

public final class Const {

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

    // Groups of words that sound alike; any word of a group can replace another
    public static final List<List<String>> HOMOPHONES = List.of(
            List.of("ate", "eight"), List.of("bare", "bear"), List.of("be", "bee"),
            List.of("blew", "blue"), List.of("brake", "break"), List.of("buy", "by", "bye"),
            List.of("cell", "sell"), List.of("cent", "scent", "sent"), List.of("dear", "deer"),
            List.of("die", "dye"), List.of("fair", "fare"), List.of("flour", "flower"),
            List.of("for", "four"), List.of("hair", "hare"), List.of("hear", "here"),
            List.of("hi", "high"), List.of("hole", "whole"), List.of("hour", "our"),
            List.of("knew", "new"), List.of("knight", "night"), List.of("know", "no"),
            List.of("mail", "male"), List.of("meat", "meet"), List.of("one", "won"),
            List.of("pair", "pear"), List.of("peace", "piece"), List.of("plain", "plane"),
            List.of("right", "write"), List.of("road", "rode"), List.of("sail", "sale"),
            List.of("sea", "see"), List.of("son", "sun"), List.of("tail", "tale"),
            List.of("their", "there"), List.of("to", "too", "two"), List.of("wait", "weight"),
            List.of("way", "weigh"), List.of("weak", "week"), List.of("wear", "where"),
            List.of("which", "witch"), List.of("wood", "would")
    );

    // Most common dynamic DNS providers, all listed in dynamic-dns.json
    public static final List<String> DYNAMIC_DNS_PROVIDERS = List.of(
            "duckdns.org", "ddns.net", "hopto.org", "zapto.org", "sytes.net", "myftp.org",
            "dyndns.org", "dynu.com", "mooo.com", "us.to", "servebeer.com", "serveftp.com",
            "redirectme.net", "3utilities.com"
    );

    // Second-level labels commonly used under country-code TLDs (example.co.uk, example.com.au...)
    public static final List<String> COMMON_SLD = List.of(
            "co", "com", "net", "org", "ac", "gov", "edu", "ltd", "plc", "me", "biz", "info"
    );

    private Const() {
        // Private constructor to prevent instantiation of this utility class
    }
}