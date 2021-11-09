/*
 * Copyright Red Hat
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.mashona.logwriting;

/**
 * Configuration settings for the AppendOnlyLogImpl.
 *
 * @author Jonathan Halliday (jonathan.halliday@redhat.com)
 * @since 2021-09
 */
public class AppendOnlyLogImplConfig {

    private final boolean blockPadding;
    private final boolean linearOrdering;
    private final boolean alwaysCheckpoint;
    private final boolean authoritativeCheckpointOnReads;

    /**
     * Creates a new configuration object for an AppendOnlyLog.
     *
     * <p>Note that setting alwaysCheckpoint=true requires also that linearOrdering=true.</p>
     * <p>Do not set authoritativeCheckpointOnReads=true when opening an existing log file, unless it was written with alwaysCheckpoint=true.</p>
     *
     * @param blockPadding true if extra space should be used to increase performance, or false for a slower but more compact record format.
     * @param linearOrdering true if strict serial ordering of writes is required, false for more relaxed ordering guarantees.
     * @param alwaysCheckpointWrites true if automatic checkpointing of writes is required, false otherwise.
     * @param authoritativeCheckpointOnReads true if the persistent checkpoint (limit) in the file should be used when reading back the log,
     *                                       false if the entries should be walked instead.
     *
     * @throws IllegalArgumentException if an unsupported combination of settings is used.
     */
    public AppendOnlyLogImplConfig(boolean blockPadding, boolean linearOrdering,
                                   boolean alwaysCheckpointWrites, boolean authoritativeCheckpointOnReads) {

        if(alwaysCheckpointWrites && !linearOrdering) {
            throw new IllegalArgumentException("linearOrdering must be true when alwaysCheckpoint is enabled");
        }

        this.blockPadding = blockPadding;
        this.linearOrdering = linearOrdering;
        this.alwaysCheckpoint = alwaysCheckpointWrites;
        this.authoritativeCheckpointOnReads = authoritativeCheckpointOnReads;
    }

    /**
     * Reports the padding mode.
     *
     * @return true if extra space should be used to increase performance, or false for a slower but more compact record format.
     */
    public boolean isBlockPadding() {
        return blockPadding;
    }

    /**
     * Reports the ordering mode.
     *
     * @return true if strict serial ordering of writes is required, false for more relaxed ordering guarantees.
     */
    public boolean isLinearOrdering() {
        return linearOrdering;
    }

    /**
     * Reports the auto-checkpointing mode.
     *
     * @return true if writes should always include an implicit checkpoint update, false otherwise.
     */
    public boolean isAlwaysCheckpoint() {
        return alwaysCheckpoint;
    }

    /**
     * Reports the read/recovery mode.
     *
     * @return true if the persistent checkpoint (limit) in the file should be used when reading back the log,
     *          false if the entries should be walked instead.
     */
    public boolean isAuthoritativeCheckpointOnReads() {
        return authoritativeCheckpointOnReads;
    }

    @Override
    public String toString() {
        return "AppendOnlyLogImplConfig{" +
                "blockPadding=" + blockPadding +
                ", linearOrdering=" + linearOrdering +
                ", alwaysCheckpoint=" + alwaysCheckpoint +
                ", authoritativeCheckpointOnReads=" + authoritativeCheckpointOnReads +
                '}';
    }
}
