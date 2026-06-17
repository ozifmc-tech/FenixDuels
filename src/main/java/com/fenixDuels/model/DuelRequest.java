package com.fenixDuels.model;

public class DuelRequest {
    private final String kitName;
    private final int rounds;

    public DuelRequest(String kitName, int rounds) {
        this.kitName = kitName;
        this.rounds = rounds;
    }

    public String getKitName() { return kitName; }
    public int getRounds() { return rounds; }
}