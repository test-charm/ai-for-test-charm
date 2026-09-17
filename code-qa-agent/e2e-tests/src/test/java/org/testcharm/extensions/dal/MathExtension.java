package org.testcharm.extensions.dal;

import org.testcharm.dal.DAL;
import org.testcharm.dal.runtime.Extension;

import java.math.BigDecimal;

public class MathExtension implements Extension {
    @Override
    public void extend(DAL dal) {
        dal.getRuntimeContextBuilder().registerStaticMethodExtension(Methods.class);
    }

    public static class Methods {

        public static boolean ge(BigDecimal a, BigDecimal b) {
            return a.compareTo(b) >= 0;
        }
    }
}
