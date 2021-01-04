/*
 * Copyright 2020 Red Hat, Inc. and/or its affiliates.
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
package io.mashona.pobj.allocator;

/**
 * Describes the configuration of a memory region in terms of its overall size and the size of the elements it contains.
 *
 * @author Jonathan Halliday (jonathan.halliday@redhat.com)
 * @since 2020-06
 */
public class RegionConfig implements Comparable<RegionConfig> {

    private final long elementSize;
    private final long backingSize;

    /**
     * Create a encapsulation of the configuration properties of a contiguous memory region,
     * comprising an overall size and the unit size of divisions within it.
     *
     * @param elementSize the allocation unit size, in bytes.
     * @param backingSize the overall region size, in bytes.
     */
    public RegionConfig(long elementSize, long backingSize) {
        this.elementSize = elementSize;
        this.backingSize = backingSize;
    }

    /**
     * Returns the element size for the region.
     *
     * @return the allocation unit size, in bytes.
     */
    public long getElementSize() {
        return elementSize;
    }

    /**
     * Returns the total size of the region.
     *
     * @return the region memory size, in bytes.
     */
    public long getBackingSize() {
        return backingSize;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int compareTo(RegionConfig o) {
        int x = Long.compare(elementSize, o.elementSize);
        if (x == 0) {
            x = Long.compare(backingSize, o.backingSize);
        }
        return x;
    }
}
