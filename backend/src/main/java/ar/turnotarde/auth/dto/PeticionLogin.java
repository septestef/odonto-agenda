package ar.turnotarde.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Cuerpo de `POST /api/auth/login` (D2). */
public record PeticionLogin(
        @NotBlank(message = "es obligatorio")
        @Email(message = "debe ser un email válido")
        @Size(max = 255, message = "es demasiado largo") String email,

        @NotBlank(message = "es obligatorio")
        @Size(max = 72, message = "es demasiado larga")
        @MaximoBytesUtf8(72) String password) {

    /**
     * El email se normaliza antes de validar: `@Email` rechaza los espacios sobrantes, pero la spec los
     * acepta y los ignora (D10). Así el mismo valor se valida, se busca y se usa como clave del bloqueo.
     */
    public PeticionLogin {
        email = email == null ? null : email.strip();
    }
}
