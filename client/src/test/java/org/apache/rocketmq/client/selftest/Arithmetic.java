package org.apache.rocketmq.client.selftest;

import org.junit.Test;

/**
 * @author : wangbiao
 * @version V1.0
 * @Project: rocketmq-all
 * @Package org.apache.rocketmq.client.selftest
 * @Description: TODO
 * @date Date : 2019年08月05日 14:10
 */
public class Arithmetic {
    @Test
    public void remainder(){
        System.out.println("0%100 equals:"+(0%100));
    }

    @Test
    public void testFor(){
        /**设置的大小**/
        int i=5;
        /**实际的大小**/
        int j=6;
        for(int m=0;m<j;){
            System.out.println("m的值："+m);
            for(int n=0;n<i;n++,m++){
                if(m<j){
                    System.out.println("n的值："+n);
                }else {
                    break;
                }
            }
        }
    }
}
