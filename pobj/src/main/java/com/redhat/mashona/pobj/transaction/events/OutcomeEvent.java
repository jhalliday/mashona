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
package com.redhat.mashona.pobj.transaction.events;

/**
 * Transaction log entry for recording terminal commit/rollback decision.
 *
 * @author Jonathan Halliday (jonathan.halliday@redhat.com)
 * @since 2020-07
 */
public class OutcomeEvent implements TransactionEvent {

    public final boolean commit;

    /**
     * Creates a record of the terminal state of a transaction.
     *
     * @param commit true for committed transactions, false for those rolled back.
     */
    public OutcomeEvent(boolean commit) {
        this.commit = commit;
    }

    public boolean isCommit() {
        return commit;
    }
}
