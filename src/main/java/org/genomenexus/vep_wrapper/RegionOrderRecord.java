package org.genomenexus.vep_wrapper;

public class RegionOrderRecord implements Comparable<RegionOrderRecord> {
    private String chromosome;
    private int startPosition;
    private int endPosition;
    private int originalOrderIndex;

    public String getChromosome() {
        return this.chromosome;
    }

    public void setChromosome(String chromosome) {
        this.chromosome = chromosome;
    }

    public int getStartPosition() {
        return this.startPosition;
    }

    public void setStartPosition(int startPosition) {
        this.startPosition = startPosition;
    }

    public int getEndPosition() {
        return this.endPosition;
    }

    public void setEndPosition(int endPosition) {
        this.endPosition = endPosition;
    }

    public int getOriginalOrderIndex() {
        return this.originalOrderIndex;
    }

    public void setOriginalOrderIndex(int originalOrderIndex) {
        this.originalOrderIndex = originalOrderIndex;
    }

    @Override
    public int compareTo(RegionOrderRecord other) {
        int comparison = chromosome.compareTo(other.getChromosome());
        if (comparison != 0) {
            return comparison;
        }
        comparison = startPosition - other.getStartPosition();
        if (comparison != 0) {
            return comparison;
        }
        comparison = endPosition - other.getEndPosition();
        if (comparison != 0) {
            return comparison;
        }
        return originalOrderIndex - other.getOriginalOrderIndex();
    }

}
