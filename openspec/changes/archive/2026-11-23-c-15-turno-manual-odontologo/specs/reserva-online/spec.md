# Spec Delta — `reserva-online`

## ADDED Requirements

### Requirement: Los turnos manuales cuentan para el límite online

El conteo de turnos `RESERVADO` futuros por DNI y odontólogo (RN-PA-03) MUST incluir los turnos creados manualmente por el odontólogo, con independencia de su `origen`. El alta manual sigue sin estar sujeta a ese límite; el límite solo se aplica a la reserva online.

#### Scenario: Dos turnos manuales bloquean una reserva online

- **GIVEN** un DNI con dos turnos manuales `RESERVADO` futuros con `demo`
- **WHEN** ese DNI intenta reservar online un tercer horario libre con `demo`
- **THEN** la API responde 409 con código `RN-PA-03` y no se crea el turno

#### Scenario: Un turno manual y uno online alcanzan el límite

- **GIVEN** un DNI con un turno manual y un turno online `RESERVADO` futuros con `demo`
- **WHEN** ese DNI intenta reservar online un tercer horario libre con `demo`
- **THEN** la API responde 409 con código `RN-PA-03`
