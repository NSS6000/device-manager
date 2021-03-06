package com.sicau.devicemanager.controller;

import com.sicau.devicemanager.POJO.DO.RepairOrder;
import com.sicau.devicemanager.POJO.DTO.RepairOrderDTO;
import com.sicau.devicemanager.POJO.VO.ResultVO;
import com.sicau.devicemanager.config.exception.BusinessException;
import com.sicau.devicemanager.config.validation.group.CommonValidatedGroup;
import com.sicau.devicemanager.config.validation.group.DeviceValidatedGroup;
import com.sicau.devicemanager.constants.*;
import com.sicau.devicemanager.service.RepairDeviceService;
import com.sicau.devicemanager.util.EnumUtil;
import com.sicau.devicemanager.util.web.RequestUtil;
import com.sicau.devicemanager.util.web.ResultVOUtil;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.apache.shiro.authz.annotation.RequiresRoles;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 维修设备
 */
@RestController
@RequestMapping(CommonConstants.API_PREFIX)
public class RepairDeviceController {

    @Autowired
    public RepairDeviceService repairDeviceService;

    /**
     * @author 郭效坤
     * 根据用户id查询所有维修设备订单
     * 如果查的是当前用户提交的维修表，则根据用户id查询
     * @return 视图对象
     */
    @PostMapping("/select-repair-order-userId")
    public ResultVO selectRepairOrderByUserId(@RequestBody RepairOrderDTO repairOrderDTO) {
        //在前台传递过来的数据中解析出userId
        repairOrderDTO.setApplyUserId(RequestUtil.getCurrentUserId());
        //如果逻辑层抽查到数据，则顺利返回，如果逻辑层未查到数据则逻辑层抛出资源不存在异常
        return ResultVOUtil.success(repairDeviceService.selectRepairOrderByUserId(repairOrderDTO));
    }

    /**
     * @author 郭效坤
     * 根据设备状态查询所有维修设备
     * 维修设备可能存在四种状态，只要传入对应状态代码即可
     * @param repairOrderDTO 传递过来的是RepairOrderDTO的对象,包含状态代码，分页信息
     * @return 视图对象
     */
    @PostMapping("/select-repair-order-statusCode")
    public ResultVO selectRepairOrderByStatus(@RequestBody RepairOrderDTO repairOrderDTO) {
        return ResultVOUtil.success(repairDeviceService.selectRepairOrderByStatus(repairOrderDTO));
    }

    /**
     * 报修设备
     * @param repairOrder
     * @return
     */
    @RequiresPermissions(ResourceConstants.ORDER + PermissionActionConstant.ADD)
    @PostMapping("/submit-repair-order")
    public ResultVO repairDevice(@Validated({DeviceValidatedGroup.SubmitRepairOrder.class, CommonValidatedGroup.LegalityGroup.class})
                                 @RequestBody RepairOrder repairOrder) {
        repairDeviceService.submitRepairDeviceOrder(repairOrder);
        return ResultVOUtil.success();
    }

    /**
     * 修改订单
     * @author Xiao W
     */
    @PostMapping("/modify-repair-order")
    public ResultVO modifyOrder(@Validated({DeviceValidatedGroup.ModifyRepairOrder.class, CommonValidatedGroup.LegalityGroup.class})
                                @RequestBody RepairOrder repairOrder) {
        repairDeviceService.modifyOrder(repairOrder);
        return ResultVOUtil.success();
    }

    /**
     * 根据设备id获取订单
     * @author Xiao W
     */
    @PostMapping("/orders/device/{deviceId}")
    public ResultVO getOrders(@PathVariable String deviceId) {
        if (StringUtils.isEmpty(deviceId)) {
            throw new BusinessException(BusinessExceptionEnum.DEVICE_ID_CANNOT_BE_NULL);
        }
        return ResultVOUtil.success(repairDeviceService.getOrdersByDeviceId(deviceId));
    }

    /**
     * 管理员（维修人员）调用完成订单
     * @author Xiao W
     */
    @RequiresPermissions(ResourceConstants.ORDER + PermissionActionConstant.FINISH_ADMIN)
    @PostMapping("/finish-order-admin")
    public ResultVO finishAdmin(@Validated({DeviceValidatedGroup.AdminFinishOrder.class})
                                @RequestBody RepairOrder repairOrder) {
        repairDeviceService.finishOrder(repairOrder.getId(), EnumUtil.getByCode(repairOrder.getStatusCode(), OrderStatusEnum.class));
        return ResultVOUtil.success();
    }


    /**
     * 用户（订单提交人员）调用完成订单
     * @author Xiao W
     */
    @RequiresPermissions(ResourceConstants.ORDER + PermissionActionConstant.FINISH_USER)
    @PostMapping("/finish-order-user")
    public ResultVO finishUser(@Validated(DeviceValidatedGroup.UserFinishOrder.class)
                               @RequestBody RepairOrder repairOrder) {
        repairDeviceService.finishOrder(repairOrder.getId(), EnumUtil.getByCode(repairOrder.getDeviceStatus(), DeviceStatusEnum.class));
        return ResultVOUtil.success();
    }

    /**
     * 删除自己的维修订单
     * @param repairId
     * @return
     */
    @PostMapping("/delete-onself-repair-order/{repairId}")
    public ResultVO deleteRepairDeviceOrder(@PathVariable Integer repairId) {
        repairDeviceService.deleteOneselfRepairDeviceOrder(repairId);
        return ResultVOUtil.success();
    }

    /**
     * 可以删除任意维修订单
     * @param repairId
     * @return
     */
    @RequiresPermissions(ResourceConstants.ORDER + PermissionActionConstant.DELETE)
    @PostMapping("/delete-any-repair-order/{repairId}")
    public ResultVO deleteAnyRepairDeviceOrder(@PathVariable Integer repairId) {
        repairDeviceService.deleteAnyRepairDeviceOrder(repairId);
        return ResultVOUtil.success();
    }
}
