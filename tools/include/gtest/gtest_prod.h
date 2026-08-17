// Minimal production header so android-tools can compile without system gtest.
// FRIEND_TEST is the only macro zip_writer.h needs.
#ifndef GOOGLETEST_INCLUDE_GTEST_GTEST_PROD_H_
#define GOOGLETEST_INCLUDE_GTEST_GTEST_PROD_H_
#define FRIEND_TEST(test_case_name, test_name) \
  friend class test_case_name##_##test_name##_Test
#endif
