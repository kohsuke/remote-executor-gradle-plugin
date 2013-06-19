package com.tngtech.java.junit.dataprovider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.TestClass;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

import java.util.List;

import static org.fest.assertions.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

public class MockitoFailingRemoteTest {
    @Spy
    private DataProviderRunner underTest;

    @Mock
    private TestClass testClass;

    @Before
    public void setup() throws Exception {
        underTest = new DataProviderRunner(DataProviderRunnerTest.class);

        MockitoAnnotations.initMocks(this);
        System.out.println("Get ready to mock");
        doReturn(testClass).when(underTest).getTestClassInt();
        System.out.println("Mocked it!");
    }

    @Test
    public void testExplodeTestMethodsShouldReturnMultipleDataProviderFrameworkMethodIfDataProviderMethodReturnsMultipleRow()
            throws Throwable {

        // Given:
        FrameworkMethod testMethod = mock(FrameworkMethod.class);
        FrameworkMethod dataProviderMethod = mock(FrameworkMethod.class);

        Object[][] dataProviderMethodResult = new Object[][] { { 1, "2", 3L }, { 4, "5", 6L }, { 7, "8", 9L } };
        doReturn(dataProviderMethodResult).when(dataProviderMethod).invokeExplosively(null);

        // When:
        List<FrameworkMethod> result = underTest.explodeTestMethod(testMethod, dataProviderMethod);

        // Then:
        assertThat(result).hasSize(3);
        assertThat(result.get(0)).isInstanceOf(DataProviderFrameworkMethod.class);

        DataProviderFrameworkMethod actual0 = (DataProviderFrameworkMethod) result.get(0);
        assertThat(actual0.idx).isEqualTo(0);
        assertThat(actual0.parameters).isEqualTo(dataProviderMethodResult[0]);

        assertThat(result.get(1)).isInstanceOf(DataProviderFrameworkMethod.class);

        DataProviderFrameworkMethod actual1 = (DataProviderFrameworkMethod) result.get(1);
        assertThat(actual1.idx).isEqualTo(1);
        assertThat(actual1.parameters).isEqualTo(dataProviderMethodResult[1]);

        assertThat(result.get(0)).isInstanceOf(DataProviderFrameworkMethod.class);

        DataProviderFrameworkMethod actual2 = (DataProviderFrameworkMethod) result.get(2);
        assertThat(actual2.idx).isEqualTo(2);
        assertThat(actual2.parameters).isEqualTo(dataProviderMethodResult[2]);
    }
}
