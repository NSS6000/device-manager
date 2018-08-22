package com.sicau.devicemanager.service.impl;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.sicau.devicemanager.POJO.DO.*;
import com.sicau.devicemanager.POJO.DTO.DeviceDTO;
import com.sicau.devicemanager.POJO.DTO.DistributeDeviceDTO;
import com.sicau.devicemanager.POJO.DTO.QueryPage;
import com.sicau.devicemanager.config.exception.CommonException;
import com.sicau.devicemanager.constants.ResultEnum;
import com.sicau.devicemanager.dao.*;
import com.sicau.devicemanager.service.DeviceService;
import com.sicau.devicemanager.util.DateUtil;
import com.sicau.devicemanager.util.KeyUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

/**
 * @author Yazhe
 * Created at 17:45 2018/8/7
 */
@Service
@Transactional(rollbackFor = Exception.class)
public class DeviceServiceImpl implements DeviceService {

	@Autowired
	private DeviceMapper deviceMapper;
	@Autowired
	private DeviceBrandMapper deviceBrandMapper;
	@Autowired
	private DeviceCategoryMapper deviceCategoryMapper;
	@Autowired
	private LocationMapper locationMapper;
	@Autowired
	private CategoryMapper categoryMapper;
	@Autowired
	private DeviceStatusRecordMapper deviceStatusRecordMapper;

	@Override
	public void addDevice(DeviceDTO deviceDTO) {
		deviceDTO.setId(KeyUtil.genUniqueKey());
		deviceDTO.setStatusId(1);
		deviceMapper.insertSelective(deviceDTO);
		deviceStatusRecordMapper.insert(
				new DeviceStatusRecord(KeyUtil.genUniqueKey(),deviceDTO.getId(),
						-1,1,deviceDTO.getUserId())
		);
		insertDeviceBrand(deviceDTO);
		insertDeviceCategory(deviceDTO);
	}

	private void insertDeviceBrand(DeviceDTO deviceDTO){
		DeviceBrand deviceBrand = new DeviceBrand();
		deviceBrand.setId(KeyUtil.genUniqueKey());
		deviceBrand.setBrandId(deviceDTO.getBrand().getId());
		deviceBrand.setDeviceId(deviceDTO.getId());
		deviceBrandMapper.insert(deviceBrand);
	}

	private void insertDeviceCategory(DeviceDTO deviceDTO){
		for (String categoryId :
				deviceDTO.getCategoryIds()) {
			DeviceCategory deviceCategory = new DeviceCategory();
			deviceCategory.setId(KeyUtil.genUniqueKey());
			deviceCategory.setCategoryId(categoryId);
			deviceCategory.setDeviceId(deviceDTO.getId());
			deviceCategoryMapper.insert(deviceCategory);
		}
	}

	@Override
	public void updateDeviceById(DeviceDTO deviceDTO) {
		deviceMapper.updateByPrimaryKeySelective(deviceDTO);

		deviceBrandMapper.deleteByDeviceId(deviceDTO.getId());
		insertDeviceBrand(deviceDTO);

		deviceCategoryMapper.deleteByDeviceId(deviceDTO.getId());
		insertDeviceCategory(deviceDTO);
	}

	@Override
	public void deleteDeviceById(List<String> ids) {
		if (ids.isEmpty()){
			return;
		}
		deviceCategoryMapper.deleteByDeviceIds(ids);
		deviceBrandMapper.deleteByDeviceIds(ids);
		deviceMapper.deleteByIds(ids);
	}

	@Override
	public PageInfo<DeviceDTO> listDevice(DeviceDTO deviceDTO) {
		//校验时间
		Date startTime = deviceDTO.getStartTime();
		Date endTime = deviceDTO.getEndTime();
		if (startTime!=null && endTime!=null){
			int result = startTime.compareTo(endTime);
			//开始时间大于结束时间
			if (result == 1){
				throw new CommonException(ResultEnum.DATE_INCORRECT.getCode(),"请选择合适的日期");
			}
			else if (result == 0){
				//开始时间=结束时间
				deviceDTO.setStartTime(DateUtil.getStartTimeToday(startTime));
				deviceDTO.setEndTime(DateUtil.getEndTimeToday(endTime));
			}
		}

		//获取查询条件的分类id及所有子分类id
		if (deviceDTO.getCategoryId() != null){
			List<Category> categoryList = categoryMapper.getDescendants(deviceDTO.getCategoryId());
			List<String> categoryIds = new ArrayList<>();
			categoryIds.add(deviceDTO.getCategoryId());
			categoryList.forEach((category) -> categoryIds.add(category.getId()));
			deviceDTO.setCategoryIds(categoryIds);
		}

		//获取查询条件的地点id及所有子地点id，若查询的地点id不属于角色管理区域下的id，则抛出异常
		String userId = deviceDTO.getUserId();
		if (userId == null){
			throw new RuntimeException("用户id不能为空");
		}
		Location location = locationMapper.getByUserId(userId);
		//获取所有子地点
		List<Location> locationList = locationMapper.getDescendants(location.getId());
		locationList.add(location);
		//开始校验地点
		if (deviceDTO.getLocationId() != null){
			if (!checkLocationId(deviceDTO.getLocationId(),locationList)){
				throw new CommonException(ResultEnum.LOCATION_UNAUTHORIZED);
			}
		}
		//设置地点id作为查询条件
		List<String> locationIds = new ArrayList<>();
		for (Location item : locationList){
			locationIds.add(item.getId());
		}
		deviceDTO.setLocationIds(locationIds);

		//分页查询
		QueryPage queryPage = deviceDTO.getQueryPage();
		//默认id升序
		if (queryPage.getOrderBy() == null){
			queryPage.setOrderBy("id");
			queryPage.setOrderDirection("asc");
		}
		PageHelper.startPage(queryPage.getPageNum(),queryPage.getPageSize(),
				queryPage.getOrderBy() + " " +queryPage.getOrderDirection());
		List<DeviceDTO> deviceDTOList = deviceMapper.getDeviceInfo(deviceDTO);
		//组装地点和分类信息
		setLocationAndCategory(deviceDTOList);
		return new PageInfo<>(deviceDTOList);
	}

	@Override
	public void distributeDevice(DistributeDeviceDTO distributeDeviceDTO) {
		distributeDeviceDTO.setUseTime(new Date());
		deviceMapper.distributeDevice(distributeDeviceDTO);
	}

	/**
	 * 校验locationId是否在目标list中
	 * @param locationId
	 * @param locationList
	 * @return 存在返回true，否则返回false
	 */
	private boolean checkLocationId(String locationId, List<Location> locationList){
		for (Location location : locationList){
			if (locationId.equals(location.getId())){
				return true;
			}
		}
		return false;
	}

	private void setLocationAndCategory(List<DeviceDTO> deviceDTOList){
		for (DeviceDTO deviceDTO : deviceDTOList){
			//地点信息
			StringBuilder locationStr = new StringBuilder();
			Location location = deviceDTO.getLocation();
			//不给用户返回location数据
			deviceDTO.setLocation(null);
			List<String> locationIds = new ArrayList<>(Arrays.asList(location.getPath().split("/")));
			//字符串分割后第一个元素为空，去掉
			if (locationIds.size()>0){
				locationIds.remove(0);
				List<Location> locationList = locationMapper.getLocationsInIds(locationIds);
				for (Location item : locationList){
					locationStr.append(item.getName());
					locationStr.append("/");
				}
				deviceDTO.setLocationStr(locationStr.append(location.getName()).toString());
			}else {
				//否则为顶级区域
				deviceDTO.setLocationStr(location.getName());
			}

			//分类信息
			StringBuilder categoryStr = new StringBuilder();
			Category category = deviceDTO.getCategory();
			//TODO 上线删除，测试用
			if (category == null){
				deviceDTO.setCategoryStr("未分类");
				continue;
			}
			deviceDTO.setCategory(null);
			List<String> categoryIds = new ArrayList<>(Arrays.asList(category.getPath().split("/")));
			if (categoryIds.size()>0){
				categoryIds.remove(0);
				List<Category> categoryList = categoryMapper.getCategoryInIds(categoryIds);
				for (Category item : categoryList){
					categoryStr.append(item.getName());
					categoryStr.append("/");
				}
				deviceDTO.setCategoryStr(categoryStr.append(category.getName()).toString());
			}else {
				deviceDTO.setCategoryStr(category.getName());
			}
		}
	}
}