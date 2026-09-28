package br.uesc.login.dto;

public record LoginResponse(String accessToken, String tokenType, long expiresIn) {
    @Override
    public String toString() { return "LoginResponse[redacted]"; }
}
