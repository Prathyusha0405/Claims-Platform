package com.chubb.claims.auth.validation;

import com.chubb.claims.auth.dto.RegisterRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** The password rules on RegisterRequest, run through the real Bean Validation engine. */
class MaxUtf8BytesValidatorTest {

    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

    private static Set<String> passwordProblems(String password) {
        Set<ConstraintViolation<RegisterRequest>> violations =
                VALIDATOR.validate(new RegisterRequest("Jane Tan", "jane@example.com", password));
        return violations.stream()
                .filter(violation -> violation.getPropertyPath().toString().equals("password"))
                .map(ConstraintViolation::getMessage)
                .collect(java.util.stream.Collectors.toSet());
    }

    @Test
    void anAsciiPasswordAtExactlyTheByteLimitIsAccepted() {
        assertThat(passwordProblems("a1" + "x".repeat(70))).isEmpty();      // 72 chars, 72 bytes
    }

    @Test
    void oneCharacterOverTheLimitIsRejectedByTheLengthRule() {
        assertThat(passwordProblems("a1" + "x".repeat(71))).isNotEmpty();   // 73 chars
    }

    @Test
    void lettersAndDigitsOfAnyScriptAreAccepted() {
        assertThat(passwordProblems("密码密码密码密码1")).isEmpty();             // Chinese + digit, 27 bytes
        assertThat(passwordProblems("ผ่านรหัส1234")).isEmpty();                  // Thai
        assertThat(passwordProblems("pässwörd١٢٣")).isEmpty();                 // accents + Arabic-Indic digits
    }

    @Test
    void aShortCharacterCountCannotHideAPasswordOverTheByteLimit() {
        String password = "密".repeat(24) + "1";                                // 25 chars but 73 bytes

        assertThat(passwordProblems(password))
                .containsExactly("must be at most 72 bytes when UTF-8 encoded; use fewer non-ASCII characters");
    }

    @Test
    void aMixedPasswordJustUnderTheByteLimitIsAccepted() {
        assertThat(passwordProblems("密".repeat(23) + "1" + "a")).isEmpty();     // 23*3 + 1 + 1 = 71 bytes
    }

    @Test
    void aPasswordStillNeedsALetterAndADigit() {
        assertThat(passwordProblems("onlyletters")).containsExactly("must contain at least one letter and one digit");
        assertThat(passwordProblems("12345678")).containsExactly("must contain at least one letter and one digit");
        assertThat(passwordProblems("!!!!????")).containsExactly("must contain at least one letter and one digit");
    }

    @Test
    void nullIsLeftToNotBlank() {
        assertThat(passwordProblems(null)).containsExactly("must not be blank");
    }
}
