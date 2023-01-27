package org.tests.model.array;

import io.ebean.*;
import io.ebean.xtest.BaseTestCase;
import io.ebean.xtest.ForPlatform;
import io.ebean.xtest.IgnorePlatform;
import io.ebean.annotation.Platform;
import io.ebean.test.LoggedSql;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class TestDbArray_basic extends BaseTestCase {

  private final EArrayBean bean = new EArrayBean();

  private EArrayBean found;

  @Test
  @IgnorePlatform(Platform.HANA)
  void insert() throws SQLException {
    DB.find(EArrayBean.class).delete();
    bean.setName("some stuff");
    assertThat(bean.getStatuses()).as("DbArray is auto initialised").isNotNull();
    assertThat(bean.getIntEnums()).as("DbArray is auto initialised").isNotNull();
    assertThat(bean.getUids()).as("DbArray is auto initialised").isNotNull();

    List<String> phNumbers = bean.getPhoneNumbers();
    phNumbers.add("4321");
    phNumbers.add("9823");

    List<BigDecimal> doubles = new ArrayList<>();
    doubles.add(BigDecimal.valueOf(1.3));
    doubles.add(BigDecimal.valueOf(2.4));

    bean.getUids().add(UUID.randomUUID());
    bean.getUids().add(UUID.randomUUID());
    bean.getOtherIds().add(95L);
    bean.getOtherIds().add(96L);
    bean.getOtherIds().add(97L);
    bean.setDecimals(doubles);
    bean.setDoubs(List.of(1.1d, 2.3d));
    bean.setFloats(List.of(1.01f, 2.02f));
    bean.setStatuses(new ArrayList<>());
    bean.getStatuses().add(EArrayBean.Status.ONE);
    bean.getStatuses().add(EArrayBean.Status.THREE);
    bean.getVcEnums().add(VarcharEnum.ONE);
    bean.getVcEnums().add(VarcharEnum.TWO);
    bean.getIntEnums().add(IntEnum.ZERO);
    bean.getIntEnums().add(IntEnum.TWO);

    bean.setStatus2(new LinkedHashSet<>());
    bean.getStatus2().add(EArrayBean.Status.TWO);
    bean.getStatus2().add(EArrayBean.Status.ONE);

    DB.save(bean);

    found = DB.find(EArrayBean.class, bean.getId());

    assertThat(found.getPhoneNumbers()).containsExactly("4321", "9823");
    assertThat(found.getDoubs()).hasSize(2);
    assertThat(found.getFloats()).hasSize(2);

    if (isPostgresCompatible()) {
      Query<EArrayBean> query = DB.find(EArrayBean.class)
        .where()
        .arrayContains("otherIds", 96L, 97L)
        .arrayContains("uids", bean.getUids().get(0))
        .arrayContains("phoneNumbers", "9823")
        .arrayIsNotEmpty("phoneNumbers")
        .arrayContains("statuses", EArrayBean.Status.ONE)
        .arrayContains("status2", EArrayBean.Status.TWO)
        .arrayContains("vcEnums", VarcharEnum.TWO)
        .arrayContains("intEnums", IntEnum.ZERO)
        .query();

      List<EArrayBean> list = query.findList();

      List<EArrayBean.Status> statuses = list.get(0).getStatuses();
      Set<EArrayBean.Status> status2 = list.get(0).getStatus2();
      List<IntEnum> intEnums = list.get(0).getIntEnums();
      List<VarcharEnum> varcharEnums = list.get(0).getVcEnums();

      assertThat(statuses).contains(EArrayBean.Status.ONE, EArrayBean.Status.THREE);
      assertThat(status2).contains(EArrayBean.Status.ONE, EArrayBean.Status.TWO);

      assertThat(intEnums).containsExactly(IntEnum.ZERO, IntEnum.TWO);
      assertThat(varcharEnums).containsExactly(VarcharEnum.ONE, VarcharEnum.TWO);

      assertSql(query).contains(" t0.other_ids @> array[?,?]::bigint[] ");
      assertSql(query).contains(" t0.uids @> array[?] ");
      assertSql(query).contains(" t0.phone_numbers @> array[?] ");
      assertSql(query).contains(" coalesce(cardinality(t0.phone_numbers),0) <> 0");
      assertThat(list).hasSize(1);

      query = DB.find(EArrayBean.class)
        .where()
        .arrayIsEmpty("otherIds")
        .arrayNotContains("uids", bean.getUids().get(0))
        .query();
      query.findList();


      final SqlRow row = DB.sqlQuery("select * from earray_bean").findOne();

      final String[] vcs = (String[]) ((java.sql.Array) row.get("vc_enums")).getArray();
      assertThat(vcs).containsExactly("xXxONE", "xXxTWO");

      final Integer[] ints = (Integer[]) ((java.sql.Array) row.get("int_enums")).getArray();
      assertThat(ints).containsExactly(100, 102);

      assertSql(query).contains(" coalesce(cardinality(t0.other_ids),0) = 0");
      assertSql(query).contains(" not (t0.uids @> array[?])");
    }

    json_parse_format();
    update_when_notDirty();
    update_when_dirty();
  }

  //@Test//(dependsOnMethods = "insert")
  void json_parse_format() {

    String asJson = DB.json().toJson(found);
    assertThat(asJson).contains("\"phoneNumbers\":[\"4321\",\"9823\"]");
    assertThat(asJson).contains("\"id\":");

    EArrayBean fromJson = DB.json().toBean(EArrayBean.class, asJson);
    assertEquals(found.getId(), fromJson.getId());
    assertEquals(found.getId(), fromJson.getId());
    assertEquals(found.getName(), fromJson.getName());
    assertThat(fromJson.getPhoneNumbers()).containsExactly("4321", "9823");
  }

  //@Test//(dependsOnMethods = "insert")
  void update_when_notDirty() {

    found.setName("jack");
    LoggedSql.start();
    DB.save(found);
    List<String> sql = LoggedSql.stop();

    // we don't update the phone numbers (as they are not dirty)
    assertSql(sql.get(0)).contains("update earray_bean set name=?, version=? where");
  }

  //@Test//(dependsOnMethods = "update_when_notDirty")
  void update_when_dirty() {

    found.getPhoneNumbers().add("9987");
    found.getUids().add(UUID.randomUUID());

    LoggedSql.start();
    DB.save(found);
    List<String> sql = LoggedSql.stop();

    assertSql(sql.get(0)).contains("update earray_bean set phone_numbers=?, uids=?, version=? where");
  }

  @Test
  @IgnorePlatform(Platform.HANA)
  void insertNulls() {
    EArrayBean bean = new EArrayBean();
    bean.setName("some nulls");
    bean.setPhoneNumbers(null);
    bean.setOtherIds(null);

    DB.save(bean);

    EArrayBean found = DB.find(EArrayBean.class, bean.getId());
    assertThat(found.getPhoneNumbers()).isNull();
    assertThat(found.getStatuses()).isEmpty();
    assertThat(found.getIntEnums()).isEmpty();
    assertThat(found.getUids()).isEmpty();

    found.setName("some nulls 2");
    DB.save(found);

    EArrayBean found2 = DB.find(EArrayBean.class)
      .where()
      .eq("id", bean.getId())
      .isNull("phoneNumbers")
      .findOne();

    assertThat(found2).isNotNull();
    assertThat(found2.getPhoneNumbers()).isNull();

    DB.delete(found);
  }

  @Test
  @IgnorePlatform(Platform.HANA)
  void insertAll_when_hasNulls() {
    EArrayBean bean = new EArrayBean();
    bean.setName("some nulls");
    bean.setPhoneNumbers(null);
    bean.setOtherIds(null);
    bean.setUids(null);

    List<EArrayBean> all = new ArrayList<>();
    all.add(bean);

    DB.saveAll(all);
    DB.deleteAll(all);
  }

  /**
   * Platforms without Array support use JSON which by default isn't including null values.
   */
  @ForPlatform({Platform.H2, Platform.POSTGRES, Platform.YUGABYTE})
  @Test
  void nullItems() {
    EArrayBean bean = new EArrayBean();
    bean.setName("null items");

    List<BigDecimal> doubles = new ArrayList<>();
    doubles.add(BigDecimal.valueOf(1.3));
    doubles.add(null);
    doubles.add(BigDecimal.valueOf(2.4));

    bean.getPhoneNumbers().add("111222333");
    bean.getPhoneNumbers().add(null);
    bean.getPhoneNumbers().add("333222111");

    bean.getOtherIds().add(15L);
    bean.getOtherIds().add(null);
    bean.getOtherIds().add(30L);
    bean.getOtherIds().add(null);

    bean.getUids().add(UUID.randomUUID());
    bean.getUids().add(null);
    bean.getUids().add(UUID.randomUUID());

    bean.setDecimals(doubles);

    bean.setStatuses(new ArrayList<>());
    bean.getStatuses().add(EArrayBean.Status.ONE);
    bean.getStatuses().add(null);
    bean.getStatuses().add(EArrayBean.Status.THREE);

    bean.getVcEnums().add(VarcharEnum.ONE);
    bean.getVcEnums().add(null);
    bean.getVcEnums().add(VarcharEnum.TWO);

    bean.getIntEnums().add(null);
    bean.getIntEnums().add(IntEnum.ZERO);
    bean.getIntEnums().add(null);
    bean.getIntEnums().add(IntEnum.TWO);

    DB.save(bean);

    found = DB.find(EArrayBean.class, bean.getId());
    assertThat(found.getPhoneNumbers()).containsExactly("111222333", null, "333222111");
    assertThat(found.getOtherIds()).containsExactly(15L, null, 30L, null);
    assertNull(found.getUids().get(1));
    assertThat(found.getDecimals()).contains(new BigDecimal("1.3"), null, new BigDecimal("2.4"));
    assertThat(found.getStatuses()).containsExactly(EArrayBean.Status.ONE, null, EArrayBean.Status.THREE);
    assertThat(found.getVcEnums()).containsExactly(VarcharEnum.ONE, null, VarcharEnum.TWO);
    assertThat(found.getIntEnums()).containsExactly(null, IntEnum.ZERO, null, IntEnum.TWO);
    DB.delete(bean);
  }

  @Test
  @IgnorePlatform(Platform.HANA)
  void hitCache() {
    List<UUID> uids = new ArrayList<>();
    uids.add(UUID.randomUUID());
    uids.add(UUID.randomUUID());

    List<EArrayBean.Status> statuses = new ArrayList<>();
    statuses.add(EArrayBean.Status.ONE);
    statuses.add(EArrayBean.Status.THREE);

    EArrayBean bean = new EArrayBean();
    bean.setName("hitCache");
    bean.setUids(uids);
    bean.setStatuses(statuses);

    DB.save(bean);
    // load cache
    final EArrayBean entry = DB.find(EArrayBean.class, bean.getId());
    assertThat(entry.getUids()).hasSameElementsAs(uids);
    assertThat(entry.getStatuses()).hasSameElementsAs(statuses);
    // hit cache
    EArrayBean found = DB.find(EArrayBean.class, bean.getId());
    assertThat(found.getUids()).hasSameElementsAs(uids);
    assertThat(found.getStatuses()).hasSameElementsAs(statuses);
  }

  @Test
  @ForPlatform({Platform.POSTGRES, Platform.YUGABYTE})
  void asDto_withArray() {
    DB.find(EArrayBean.class).delete();
    bean.setName("array in dto test");

    List<String> phNumbers = bean.getPhoneNumbers();
    phNumbers.add("4321");
    phNumbers.add("9823");
    assertThat(bean.getDecimals()).isNull();

    List<BigDecimal> decimals = new ArrayList<>();
    decimals.add(BigDecimal.valueOf(1.23));
    decimals.add(BigDecimal.valueOf(4.56));
    bean.setDecimals(decimals);
    DB.save(bean);
    // Data is saved correctly

    LoggedSql.start();
    DtoQuery<EArrayBeanDto> query = DB.find(EArrayBean.class)
      // Interestingly writing `select("id,name,phone_numbers,doubs")`
      //   generates `select t0.id, t0.name, t0.id, t0.doubs`
      //   surprisingly changing unknown property to id
      .select("id,name,phoneNumbers,decimals")
      .asDto(EArrayBeanDto.class)
      // Shouldn't be necessary
      // But without it I see error
      //  Unable to map DB column phone_numbers to a property with a setter method on class org.tests.model.array.TestDbArray_basic$EArrayBeanDto
      .setRelaxedMode();

    List<EArrayBeanDto> dtos = query.findList();

    List<String> sql = LoggedSql.stop();
    assertSql(sql.get(0)).contains("select t0.id, t0.name, t0.phone_numbers, t0.decimals");

    for (EArrayBeanDto dto : dtos) {
      assertThat(dto.id).isNotNull();
      assertThat(dto.name).isNotNull();
      // Failure: null
      assertThat(dto.phoneNumbers).isNotNull();
      // Failure: null
      assertThat(dto.decimals).isNotNull();
    }
  }

  @Test
  @ForPlatform({Platform.POSTGRES, Platform.YUGABYTE})
  void sqlUpdate_withArray() {
    DB.find(EArrayBean.class).delete();
    bean.setName("array in sql update test");
    DB.save(bean);
    // Data is saved correctly
    List<String> phNumbers = new ArrayList<>();
    phNumbers.add("4321");
    phNumbers.add("9823");

    LoggedSql.start();

    // Positional param works
    SqlUpdate update1 = DB.sqlUpdate("UPDATE earray_bean SET phone_numbers = ?")
      .setParameter(1, phNumbers);
    update1.execute();

    SqlUpdate update2 = DB.sqlUpdate("UPDATE earray_bean SET phone_numbers = :pns")
      .setArrayParameter("pns", phNumbers);
    update2.execute();

    found = DB.find(EArrayBean.class, bean.getId());
    System.out.println(found);
    assertEquals("array in sql update test", found.getName());
    assertThat(found.getPhoneNumbers()).containsExactlyInAnyOrder("4321", "9823");
  }

  public static class EArrayBeanDto {

    Integer id;
    List<String> phoneNumbers;
    List<BigDecimal> decimals;
    String name;

    @Override
    public String toString() {
      return "id:" + id + " name:" + name + " phoneNumbers:" + phoneNumbers + " doubs:" + decimals;
    }

    public Integer getId() {
      return id;
    }

    public void setId(Integer id) {
      this.id = id;
    }

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }

    public List<String> getPhoneNumbers() {
      return phoneNumbers;
    }

    public void setPhoneNumbers(List<String> phoneNumbers) {
      this.phoneNumbers = phoneNumbers;
    }

    public List<BigDecimal> getDecimals() {
      return decimals;
    }

    public void setDecimals(List<BigDecimal> decimals) {
      this.decimals = decimals;
    }
  }
}
