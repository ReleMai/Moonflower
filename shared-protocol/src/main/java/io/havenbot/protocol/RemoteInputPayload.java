package io.havenbot.protocol;

public record RemoteInputPayload(
        RemoteInputType inputType,
        Integer x,
        Integer y,
        Integer button,
        Integer keyCode,
        String text
) {
}

