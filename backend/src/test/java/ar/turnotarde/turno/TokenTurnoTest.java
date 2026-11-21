package ar.turnotarde.turno;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Base64;
import org.junit.jupiter.api.Test;

class TokenTurnoTest {

    @Test
    void generaUnTokenBase64UrlDe43CaracteresSinPaddingQueDecodificaA32Bytes() {
        TokenTurno token = TokenTurno.generar();

        assertThat(token.plano()).hasSize(43).matches("[A-Za-z0-9_-]{43}");
        assertThat(Base64.getUrlDecoder().decode(token.plano())).hasSize(32);
    }

    @Test
    void elHashEsSha256HexadecimalEnMinusculaDelTokenPlano() {
        TokenTurno token = TokenTurno.generar();

        assertThat(token.hash()).hasSize(64).matches("[0-9a-f]{64}");
        assertThat(token.hash()).isEqualTo(TokenTurno.hashear(token.plano()));
    }

    @Test
    void hashearCoincideConUnVectorConocidoDeSha256() {
        assertThat(TokenTurno.hashear("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
        assertThat(TokenTurno.hashear(""))
                .isEqualTo("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
    }

    @Test
    void dosTokensGeneradosSonDistintos() {
        TokenTurno a = TokenTurno.generar();
        TokenTurno b = TokenTurno.generar();

        assertThat(a.plano()).isNotEqualTo(b.plano());
        assertThat(a.hash()).isNotEqualTo(b.hash());
    }

    @Test
    void toStringNoMuestraElTokenPlano() {
        TokenTurno token = TokenTurno.generar();

        assertThat(token.toString()).doesNotContain(token.plano());
    }
}
