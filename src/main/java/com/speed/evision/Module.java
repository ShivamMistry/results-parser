package com.speed.evision;

import java.util.Collections;
import java.util.List;

public class Module {
    public final String year;
    public final String moduleCode;
    public final String name;
    public final int credits;
    public final int mark;
    private List<Result> results;

    public static class Result {
        public final String name;
        public final int weighting;
        public final int attempt;
        public final int uncomfirmedResult;
        public final int confirmedResult;

        protected Result(String name, int weighting, int attempt, int uncomfirmedResult, int confirmedResult) {
            this.name = name;
            this.weighting = weighting;
            this.attempt = attempt;
            this.uncomfirmedResult = uncomfirmedResult;
            this.confirmedResult = confirmedResult;
        }

        @Override
        public String toString() {
            return "Result{" +
                    "name='" + name + '\'' +
                    ", weighting=" + weighting +
                    ", attempt=" + attempt +
                    ", uncomfirmedResult=" + uncomfirmedResult +
                    ", confirmedResult=" + confirmedResult +
                    '}';
        }
    }

    protected Module(String year, String moduleCode, String name, int credits, int mark) {
        this.year = year;
        this.moduleCode = moduleCode;
        this.name = name;
        this.credits = credits;
        this.mark = mark;
    }

    @Override
    public String toString() {
        return "Module{" +
                "year='" + year + '\'' +
                ", moduleCode='" + moduleCode + '\'' +
                ", name='" + name + '\'' +
                ", credits=" + credits +
                ", mark=" + mark +
                '}';
    }

    protected void setResults(List<Result> results) {
        this.results = results;
    }

    public List<Result> getResults() {
        return Collections.unmodifiableList(results);
    }
}
