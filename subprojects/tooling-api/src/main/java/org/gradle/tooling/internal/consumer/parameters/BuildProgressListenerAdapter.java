/*
 * Copyright 2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gradle.tooling.internal.consumer.parameters;

import org.gradle.api.Nullable;
import org.gradle.internal.event.ListenerBroadcast;
import org.gradle.tooling.Failure;
import org.gradle.tooling.events.OperationDescriptor;
import org.gradle.tooling.events.task.TaskFailureResult;
import org.gradle.tooling.events.task.TaskFinishEvent;
import org.gradle.tooling.events.task.TaskOperationDescriptor;
import org.gradle.tooling.events.task.TaskOperationResult;
import org.gradle.tooling.events.task.TaskProgressEvent;
import org.gradle.tooling.events.task.TaskProgressListener;
import org.gradle.tooling.events.task.TaskSkippedResult;
import org.gradle.tooling.events.task.TaskStartEvent;
import org.gradle.tooling.events.task.TaskSuccessResult;
import org.gradle.tooling.events.task.internal.DefaultTaskFinishedEvent;
import org.gradle.tooling.events.task.internal.DefaultTaskStartEvent;
import org.gradle.tooling.events.test.JvmTestKind;
import org.gradle.tooling.events.test.JvmTestOperationDescriptor;
import org.gradle.tooling.events.test.TestFinishEvent;
import org.gradle.tooling.events.test.TestOperationDescriptor;
import org.gradle.tooling.events.test.TestOperationResult;
import org.gradle.tooling.events.test.TestProgressEvent;
import org.gradle.tooling.events.test.TestProgressListener;
import org.gradle.tooling.events.test.TestStartEvent;
import org.gradle.tooling.events.test.internal.DefaultTestFailureResult;
import org.gradle.tooling.events.test.internal.DefaultTestFinishEvent;
import org.gradle.tooling.events.test.internal.DefaultTestSkippedResult;
import org.gradle.tooling.events.test.internal.DefaultTestStartEvent;
import org.gradle.tooling.events.test.internal.DefaultTestSuccessResult;
import org.gradle.tooling.internal.consumer.DefaultFailure;
import org.gradle.tooling.internal.protocol.InternalBuildProgressListener;
import org.gradle.tooling.internal.protocol.InternalFailure;
import org.gradle.tooling.internal.protocol.InternalTaskProgressListener;
import org.gradle.tooling.internal.protocol.events.InternalJvmTestDescriptor;
import org.gradle.tooling.internal.protocol.events.InternalTaskDescriptor;
import org.gradle.tooling.internal.protocol.events.InternalTaskFailureResult;
import org.gradle.tooling.internal.protocol.events.InternalTaskFinishedProgressEvent;
import org.gradle.tooling.internal.protocol.events.InternalTaskProgressEvent;
import org.gradle.tooling.internal.protocol.events.InternalTaskResult;
import org.gradle.tooling.internal.protocol.events.InternalTaskSkippedResult;
import org.gradle.tooling.internal.protocol.events.InternalTaskStartedProgressEvent;
import org.gradle.tooling.internal.protocol.events.InternalTaskSuccessResult;
import org.gradle.tooling.internal.protocol.events.InternalTestDescriptor;
import org.gradle.tooling.internal.protocol.events.InternalTestFailureResult;
import org.gradle.tooling.internal.protocol.events.InternalTestFinishedProgressEvent;
import org.gradle.tooling.internal.protocol.events.InternalTestProgressEvent;
import org.gradle.tooling.internal.protocol.events.InternalTestResult;
import org.gradle.tooling.internal.protocol.events.InternalTestSkippedResult;
import org.gradle.tooling.internal.protocol.events.InternalTestStartedProgressEvent;
import org.gradle.tooling.internal.protocol.events.InternalTestSuccessResult;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Converts progress events sent from the tooling provider to the tooling client to the corresponding event types available on the public Tooling API, and broadcasts the converted events to the
 * matching progress listeners. This adapter handles all the different incoming progress event types (except the original logging-derived progress listener).
 */
class BuildProgressListenerAdapter implements InternalBuildProgressListener, InternalTaskProgressListener {

    private final ListenerBroadcast<TestProgressListener> testProgressListeners = new ListenerBroadcast<TestProgressListener>(TestProgressListener.class);
    private final ListenerBroadcast<TaskProgressListener> taskProgressListeners = new ListenerBroadcast<TaskProgressListener>(TaskProgressListener.class);
    private final Map<Object, OperationDescriptor> descriptorCache = new HashMap<Object, OperationDescriptor>();

    BuildProgressListenerAdapter(List<TestProgressListener> testListeners, List<TaskProgressListener> taskListeners) {
        this.testProgressListeners.addAll(testListeners);
        this.taskProgressListeners.addAll(taskListeners);
    }

    @Override
    public List<String> getSubscribedOperations() {
        if (this.testProgressListeners.isEmpty() && this.taskProgressListeners.isEmpty()) {
            return Collections.<String>emptyList();
        }
        if (this.taskProgressListeners.isEmpty()) {
            return Collections.singletonList(InternalBuildProgressListener.TEST_EXECUTION);
        } else if (this.testProgressListeners.isEmpty()) {
            return Collections.singletonList(InternalTaskProgressListener.TASK_EXECUTION);
        }
        return Arrays.asList(InternalBuildProgressListener.TEST_EXECUTION, InternalTaskProgressListener.TASK_EXECUTION);
    }

    @Override
    public void onEvent(final Object event) {
        if (event instanceof InternalTestProgressEvent) {
            broadcastTestProgressEvent((InternalTestProgressEvent) event);
        } else if (event instanceof InternalTaskProgressEvent) {
            broadcastTaskProgressEvent((InternalTaskProgressEvent) event);
        }
    }

    private void broadcastTestProgressEvent(InternalTestProgressEvent event) {
        TestProgressEvent testProgressEvent = toTestProgressEvent(event);
        if (testProgressEvent != null) {
            testProgressListeners.getSource().statusChanged(testProgressEvent);
        }
    }

    private void broadcastTaskProgressEvent(InternalTaskProgressEvent event) {
        TaskProgressEvent taskProgressEvent = toTaskProgressEvent(event);
        if (taskProgressEvent != null) {
            taskProgressListeners.getSource().statusChanged(taskProgressEvent);
        }
    }

    private synchronized TaskProgressEvent toTaskProgressEvent(InternalTaskProgressEvent event) {
        if (event instanceof InternalTaskStartedProgressEvent) {
            return taskStartedEvent((InternalTaskStartedProgressEvent) event);
        }
        if (event instanceof InternalTaskFinishedProgressEvent) {
            return taskFinishedEvent((InternalTaskFinishedProgressEvent) event);
        }
        return null;
    }

    private synchronized TestProgressEvent toTestProgressEvent(final InternalTestProgressEvent event) {
        if (event instanceof InternalTestStartedProgressEvent) {
            return testStartedEvent((InternalTestStartedProgressEvent) event);
        } else if (event instanceof InternalTestFinishedProgressEvent) {
            return testFinishedEvent((InternalTestFinishedProgressEvent) event);
        } else {
            return null;
        }
    }

    private TaskStartEvent taskStartedEvent(InternalTaskStartedProgressEvent event) {
        TaskOperationDescriptor descriptor = addTaskDescriptor(event.getDescriptor());
        return new DefaultTaskStartEvent(event.getEventTime(), event.getDisplayName(), descriptor);
    }

    private static TaskOperationDescriptor toTaskDescriptor(final InternalTaskDescriptor descriptor) {
        return new TaskOperationDescriptor() {
            @Override
            public String getTaskPath() {
                return (String) descriptor.getId();
            }

            @Override
            public String getName() {
                return descriptor.getName();
            }

            @Override
            public String getDisplayName() {
                return descriptor.getDisplayName();
            }

            @Nullable
            @Override
            public OperationDescriptor getParent() {
                return null;
            }
        };
    }

    private TaskFinishEvent taskFinishedEvent(InternalTaskFinishedProgressEvent event) {
        TaskOperationDescriptor descriptor = removeTestDescriptor(event.getDescriptor());
        return new DefaultTaskFinishedEvent(event.getEventTime(), event.getDisplayName(), descriptor, toTaskResult(event.getResult()));
    }

    private static TaskOperationResult toTaskResult(final InternalTaskResult result) {
        if (result instanceof InternalTaskSkippedResult) {
            return new TaskSkippedResult() {
                @Override
                public boolean isUpToDate() {
                    return ((InternalTaskSkippedResult) result).isUpToDate();
                }

                @Override
                public long getStartTime() {
                    return result.getStartTime();
                }

                @Override
                public long getEndTime() {
                    return result.getEndTime();
                }
            };
        }
        if (result instanceof InternalTaskFailureResult) {
            return new TaskFailureResult() {
                @Override
                public List<? extends Failure> getFailures() {
                    InternalFailure failure = result.getFailure();
                    if (failure == null) {
                        return Collections.emptyList();
                    }
                    return Collections.singletonList(toFailure(failure));
                }

                @Override
                public long getStartTime() {
                    return result.getStartTime();
                }

                @Override
                public long getEndTime() {
                    return result.getEndTime();
                }
            };
        }
        if (result instanceof InternalTaskSuccessResult) {
            return new TaskSuccessResult() {
                @Override
                public long getStartTime() {
                    return result.getStartTime();
                }

                @Override
                public long getEndTime() {
                    return result.getEndTime();
                }
            };
        }
        return null;
    }

    private TestStartEvent testStartedEvent(InternalTestStartedProgressEvent event) {
        long eventTime = event.getEventTime();
        String displayName = event.getDisplayName();
        TestOperationDescriptor testDescriptor = addTestDescriptor(event.getDescriptor());
        return new DefaultTestStartEvent(eventTime, displayName, testDescriptor);
    }

    private TestFinishEvent testFinishedEvent(final InternalTestFinishedProgressEvent event) {
        long eventTime = event.getEventTime();
        String displayName = event.getDisplayName();
        TestOperationDescriptor testDescriptor = removeTestDescriptor(event.getDescriptor());
        TestOperationResult result = toTestResult(event.getResult());
        return new DefaultTestFinishEvent(eventTime, displayName, testDescriptor, result);
    }

    private TestOperationDescriptor addTestDescriptor(InternalTestDescriptor testDescriptor) {
        OperationDescriptor cachedTestDescriptor = this.descriptorCache.get(testDescriptor.getId());
        if (cachedTestDescriptor != null) {
            assertDescriptorType(TestOperationDescriptor.class, cachedTestDescriptor);
            throw new IllegalStateException(String.format("Operation %s already available.", toString(testDescriptor)));
        }
        final TestOperationDescriptor parent = getParentTestDescriptor(testDescriptor);
        TestOperationDescriptor newTestDescriptor = toTestDescriptor(testDescriptor, parent);
        descriptorCache.put(testDescriptor.getId(), newTestDescriptor);
        return newTestDescriptor;
    }

    private TaskOperationDescriptor addTaskDescriptor(InternalTaskDescriptor internalTaskDescriptor) {
        OperationDescriptor cachedTestDescriptor = this.descriptorCache.get(internalTaskDescriptor.getId());
        if (cachedTestDescriptor != null) {
            assertDescriptorType(TaskOperationDescriptor.class, cachedTestDescriptor);
            throw new IllegalStateException(String.format("Operation %s already available.", toString(internalTaskDescriptor)));
        }
        TaskOperationDescriptor newTaskDescriptor = toTaskDescriptor(internalTaskDescriptor);
        descriptorCache.put(internalTaskDescriptor.getId(), newTaskDescriptor);
        return newTaskDescriptor;
    }

    @SuppressWarnings("unchecked")
    private <T extends OperationDescriptor> T assertDescriptorType(Class<T> type, OperationDescriptor cachedDescriptor) {
        Class<? extends OperationDescriptor> cachedDescriptorClass = cachedDescriptor.getClass();
        if (!type.isAssignableFrom(cachedDescriptorClass)) {
            throw new IllegalStateException(String.format("Unexpected descriptor type. Required %s but found %s", type.getName(), cachedDescriptorClass.getName()));
        }
        return (T) cachedDescriptor;
    }

    private TestOperationDescriptor removeTestDescriptor(InternalTestDescriptor testDescriptor) {
        OperationDescriptor cachedTestDescriptor = this.descriptorCache.remove(testDescriptor.getId());
        if (cachedTestDescriptor == null) {
            throw new IllegalStateException(String.format("Operation %s is not available.", toString(testDescriptor)));
        }
        return assertDescriptorType(TestOperationDescriptor.class, cachedTestDescriptor);
    }

    private TaskOperationDescriptor removeTestDescriptor(InternalTaskDescriptor testDescriptor) {
        OperationDescriptor cachedTestDescriptor = this.descriptorCache.remove(testDescriptor.getId());
        if (cachedTestDescriptor == null) {
            throw new IllegalStateException(String.format("Operation %s is not available.", toString(testDescriptor)));
        }
        return assertDescriptorType(TaskOperationDescriptor.class, cachedTestDescriptor);
    }

    private static TestOperationDescriptor toTestDescriptor(final InternalTestDescriptor testDescriptor, final TestOperationDescriptor parent) {
        if (testDescriptor instanceof InternalJvmTestDescriptor) {
            final InternalJvmTestDescriptor jvmTestDescriptor = (InternalJvmTestDescriptor) testDescriptor;
            return new JvmTestOperationDescriptor() {
                @Override
                public String getName() {
                    return jvmTestDescriptor.getName();
                }

                @Override
                public String getDisplayName() {
                    return jvmTestDescriptor.getDisplayName();
                }

                @Override
                public JvmTestKind getJvmTestKind() {
                    return toJvmTestKind(jvmTestDescriptor);
                }

                @Override
                public String getSuiteName() {
                    return jvmTestDescriptor.getSuiteName();
                }

                @Override
                public String getClassName() {
                    return jvmTestDescriptor.getClassName();
                }

                @Override
                public String getMethodName() {
                    return jvmTestDescriptor.getMethodName();
                }

                @Override
                public TestOperationDescriptor getParent() {
                    return parent;
                }

                @Override
                public String toString() {
                    return getDisplayName();
                }
            };
        } else {
            return new TestOperationDescriptor() {
                @Override
                public String getName() {
                    return testDescriptor.getName();
                }

                @Override
                public String getDisplayName() {
                    return testDescriptor.getDisplayName();
                }

                @Override
                public TestOperationDescriptor getParent() {
                    return parent;
                }

                @Override
                public String toString() {
                    return getDisplayName();
                }
            };
        }
    }

    private static JvmTestKind toJvmTestKind(InternalJvmTestDescriptor jvmTestDescriptor) {
        String jvmTestKind = jvmTestDescriptor.getTestKind();
        if (InternalJvmTestDescriptor.KIND_SUITE.equals(jvmTestKind)) {
            return JvmTestKind.SUITE;
        } else if (InternalJvmTestDescriptor.KIND_ATOMIC.equals(jvmTestKind)) {
            return JvmTestKind.ATOMIC;
        } else {
            return JvmTestKind.UNKNOWN;
        }
    }

    private TestOperationResult toTestResult(final InternalTestResult result) {
        if (result instanceof InternalTestSuccessResult) {
            return new DefaultTestSuccessResult(result.getStartTime(), result.getEndTime());
        } else if (result instanceof InternalTestSkippedResult) {
            return new DefaultTestSkippedResult(result.getStartTime(), result.getEndTime());
        } else if (result instanceof InternalTestFailureResult) {
            return new DefaultTestFailureResult(result.getStartTime(), result.getEndTime(), toFailures(result));
        } else {
            return null;
        }
    }

    private static List<Failure> toFailures(InternalTestResult testResult) {
        List<Failure> failures = new ArrayList<Failure>();
        for (InternalFailure origFailure : testResult.getFailures()) {
            failures.add(toFailure(origFailure));
        }
        return failures;
    }

    private static Failure toFailure(InternalFailure origFailure) {
        return origFailure == null ? null : new DefaultFailure(
                origFailure.getMessage(),
                origFailure.getDescription(),
                toFailure(origFailure.getCauses()));
    }

    private static List<Failure> toFailure(List<? extends InternalFailure> causes) {
        List<Failure> result = new ArrayList<Failure>();
        for (InternalFailure cause : causes) {
            result.add(toFailure(cause));
        }
        return result;
    }

    private TestOperationDescriptor getParentTestDescriptor(InternalTestDescriptor testDescriptor) {
        Object parentId = testDescriptor.getParentId();
        if (parentId == null) {
            return null;
        } else {
            OperationDescriptor parentTestDescriptor = descriptorCache.get(parentId);
            if (parentTestDescriptor == null) {
                throw new IllegalStateException(String.format("Parent test descriptor with id %s not available for %s.", parentId, toString(testDescriptor)));
            } else {
                return assertDescriptorType(TestOperationDescriptor.class, parentTestDescriptor);
            }
        }
    }

    private static String toString(InternalTestDescriptor testDescriptor) {
        if (testDescriptor instanceof InternalJvmTestDescriptor) {
            return String.format("TestOperationDescriptor[id(%s), name(%s), className(%s), parent(%s)]",
                    testDescriptor.getId(), testDescriptor.getName(), ((InternalJvmTestDescriptor) testDescriptor).getClassName(), testDescriptor.getParentId());
        } else {
            return String.format("TestOperationDescriptor[id(%s), name(%s), parent(%s)]", testDescriptor.getId(), testDescriptor.getName(), testDescriptor.getParentId());
        }
    }

    private static String toString(InternalTaskDescriptor taskDescriptor) {
        return String.format("TaskOperationDescriptor[id(%s), name(%s), parent(%s)]", taskDescriptor.getId(), taskDescriptor.getName(), taskDescriptor.getParentId());
    }

}
