package com.sorbonne.backend.catalog;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Conservative offline fallback; an explicit language always takes precedence. */
final class LocalBookLanguageDetector {
    private static final Pattern WORD = Pattern.compile("\\p{L}+");
    private static final Map<String, String> LANGUAGE_CODES = languageNames();
    private static final Map<String, Set<String>> COMMON_WORDS = Map.ofEntries(
            words("en", "the and of to in that was he his with had for not is as it be you but on her at from this they were which she have would their"),
            words("fr", "le la les des et un une du de dans que qui pour pas est il elle avec sur au aux ne son sa ses mais par se ce cette était avait"),
            words("de", "der die das und ist war nicht ein eine den dem des mit sich von zu auf sie er es als auch wie aber nach noch aus bei wir"),
            words("es", "el la los las del que en y un una por con para no se su sus al lo como más pero era había este esta sin sobre"),
            words("it", "il lo la gli le di che e un una in non per con del della si era nel nella ma come più suo sua sono questo questa"),
            words("pt", "o a os as que de do da dos das em um uma não com para por se ao sua seu como mas mais era tinha foi pelo pela"),
            words("nl", "de het een en van dat die hij zij zijn haar niet met op te voor aan als maar om door ook was er deze uit naar ik"),
            words("sv", "och det att en ett som på av den till är var med för han hon inte om sig men jag så hade från skulle mig detta"),
            words("da", "og det at en et som på af den til er var med for han hun ikke om sig men jeg så havde fra skulle mig dette"),
            words("no", "og det at en et som på av den til er var med for han hun ikke om seg men jeg så hadde fra skulle meg dette"),
            words("fi", "ja on oli että hän se ei mutta kun niin kuin hänen jotka tämä sen myös mitä olla sitten ovat vain kanssa minä tai jos vielä"),
            words("pl", "i w z na się nie do że to a jest jego jak ale za od po o go tak przez dla już był była jej który co"),
            words("cs", "a v na se že to je s do z jak ale jeho byl byla pro jako tak za od co i jsou při nebo když jsem"),
            words("hu", "a az és hogy nem egy is volt meg de el ha mint ezt azt már van csak még én ő aki úgy vagy minden után"),
            words("ro", "și de în la cu un o nu pe este din că care se mai pentru lui să ce dar era ea el au fost când"),
            words("tr", "bir ve bu da de için ile o ne gibi çok daha ama olan kadar sonra ben sen onu her değil var mi ki olarak"),
            words("la", "et in est non ad cum ut qui quae quod sed ex eius esse sunt nec per autem ab quam enim atque quo erat ac hoc"),
            words("eo", "la kaj de en al ne li ŝi ĝi estas estis ke kiu kun por sur sed kiel ili mi vi tiu per pri el aŭ ankaŭ"),
            words("ru", "и в не на что он с как его я но она это по из к у за от все так был было для то же они мы"),
            words("uk", "і в не на що він з як його я але вона це по із до у за від все так був було для та вони ми"),
            words("bg", "и в не на че той с как неговата аз но тя това по от към за беше бяха като да се те сме при още")
    );

    private LocalBookLanguageDetector() {}

    static String languageCode(String raw) {
        if (raw == null) return null;
        return LANGUAGE_CODES.get(raw.strip().toLowerCase(Locale.ROOT));
    }

    static String detect(String text) {
        // Prefer the body over English production notes in otherwise non-English books.
        String sample = text.length() > 12000 ? text.substring(4000) : text;
        Map<Character.UnicodeScript, Integer> scripts = new HashMap<>();
        sample.codePoints().filter(Character::isLetter).forEach(c ->
                scripts.merge(Character.UnicodeScript.of(c), 1, Integer::sum));
        int letters = scripts.values().stream().mapToInt(Integer::intValue).sum();
        if (letters < 80) return null;
        int kana = scripts.getOrDefault(Character.UnicodeScript.HIRAGANA, 0)
                + scripts.getOrDefault(Character.UnicodeScript.KATAKANA, 0);
        if (kana >= 20 && kana > letters * .05) return "ja";
        for (var pair : List.of(
                Map.entry(Character.UnicodeScript.GREEK, "el"),
                Map.entry(Character.UnicodeScript.HANGUL, "ko"),
                Map.entry(Character.UnicodeScript.HEBREW, "he"),
                Map.entry(Character.UnicodeScript.HAN, "zh"))) {
            if (scripts.getOrDefault(pair.getKey(), 0) > letters * .6) return pair.getValue();
        }
        Map<String, Integer> counts = new HashMap<>();
        Matcher tokens = WORD.matcher(sample.toLowerCase(Locale.ROOT));
        int total = 0;
        while (tokens.find()) {
            counts.merge(tokens.group(), 1, Integer::sum);
            total++;
        }
        String best = null;
        int bestScore = 0, secondScore = 0, bestDistinct = 0;
        for (var language : COMMON_WORDS.entrySet()) {
            int score = 0, distinct = 0;
            for (String word : language.getValue()) {
                int count = counts.getOrDefault(word, 0);
                if (count > 0) distinct++;
                score += count;
            }
            if (score > bestScore) {
                secondScore = bestScore;
                bestScore = score;
                bestDistinct = distinct;
                best = language.getKey();
            } else if (score > secondScore) secondScore = score;
        }
        return bestDistinct >= 5 && bestScore >= 10 && bestScore >= total * .05
                && bestScore > secondScore * 1.2 ? best : null;
    }

    private static Map.Entry<String, Set<String>> words(String code, String words) {
        return Map.entry(code, Set.of(words.split(" ")));
    }

    private static Map<String, String> languageNames() {
        Map<String, String> names = new HashMap<>();
        for (String code : Locale.getISOLanguages()) {
            Locale locale = Locale.forLanguageTag(code);
            names.put(code, code);
            names.put(locale.getISO3Language(), code);
            for (Locale display : List.of(Locale.ENGLISH, Locale.FRENCH, locale)) {
                names.put(locale.getDisplayLanguage(display).toLowerCase(Locale.ROOT), code);
            }
        }
        names.put("francais", "fr");
        return Map.copyOf(names);
    }
}
