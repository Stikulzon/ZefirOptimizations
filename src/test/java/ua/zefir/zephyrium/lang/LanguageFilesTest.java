package ua.zefir.zephyrium.lang;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class LanguageFilesTest {
  private static final String FORMAT_SPECIFIER = "%s";
  private static final String LANG_PATH = "/assets/zephyrium/lang/%s.json";
  private static final String ENGLISH = "en_us";

  private static List<String> locales() {
    return List.of("uk_ua");
  }

  private static Map<String, String> load(String locale) throws IOException {
    try (InputStream stream =
        LanguageFilesTest.class.getResourceAsStream(LANG_PATH.formatted(locale))) {
      assertNotNull(stream, "Missing language file " + locale);
      return new Gson()
          .fromJson(
              new InputStreamReader(stream, StandardCharsets.UTF_8),
              new TypeToken<Map<String, String>>() {}.getType());
    }
  }

  private static long specifiers(String text) {
    return (text.length() - text.replace(FORMAT_SPECIFIER, "").length())
        / FORMAT_SPECIFIER.length();
  }

  @ParameterizedTest
  @MethodSource("locales")
  void hasSameKeysAndPlaceholdersAsEnglish(String locale) throws IOException {
    Map<String, String> english = load(ENGLISH);
    Map<String, String> translated = load(locale);
    assertEquals(english.keySet(), translated.keySet(), locale);
    english.forEach(
        (key, value) -> {
          assertEquals(specifiers(value), specifiers(translated.get(key)), locale + " " + key);
          assertFalse(translated.get(key).isBlank(), locale + " " + key);
        });
  }
}
