package com.example.foodorderingapp.Repositories

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.foodorderingapp.Response.CustomResponse
import com.example.foodorderingapp.Utils.Constants.ORDDER_REFRENCE
import com.example.foodorderingapp.Utils.Constants.ORDER_PROCEED
import com.example.foodorderingapp.Utils.Helper
import com.example.foodorderingapp.Worker.UpdateSalesCountWorker
import com.example.foodorderingapp.models.*
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import javax.inject.Inject

class OrderRepository @Inject constructor(private val workManager: WorkManager) {
    private val databaseReference = FirebaseDatabase.getInstance().getReference()
    private var valueEventListener: ValueEventListener? = null
    private var valueEventListenerOrdersList: ValueEventListener? = null

    private val _orderDelivery = MutableLiveData<CustomResponse<OrderTracking>>()
    val orderDelivery: LiveData<CustomResponse<OrderTracking>>
        get() = _orderDelivery

    private val _proceededOrderList =
        MutableLiveData<CustomResponse<List<Order>>>(CustomResponse.Loading())
    val proceededOrderList: LiveData<CustomResponse<List<Order>>>
        get() = _proceededOrderList


    fun createOrder(order: Order, callback: (Boolean, Exception?, String?) -> Unit) {
        val id = databaseReference.push().key ?: Helper.generateRandomStringWithTime()
        order.orderId = id
        databaseReference.child(ORDDER_REFRENCE).child(id).setValue(order)
            .addOnSuccessListener {
                callback(true, null, order.orderId)
                order.cartItemList.forEach {
                    val inputData = workDataOf(
                        UpdateSalesCountWorker.KEY_FOOD_ITEM_ID to it.foodItem.id,
                        UpdateSalesCountWorker.KEY_QUANTITY to it.quantity
                    )

                    val request = OneTimeWorkRequestBuilder<UpdateSalesCountWorker>()
                        .setInputData(inputData)
                        .build()
                    workManager.enqueue(request)

                }
            }
            .addOnFailureListener { exception ->
                callback(false, exception, null)
            }
    }

    fun updateOrderStatus(
        orderId: String,
        orderStatus: OrderTracking,
        callback: (Boolean, Exception?) -> Unit,
    ) {
        databaseReference.child(ORDDER_REFRENCE).child(orderId)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(dataSnapshot: DataSnapshot) {
                    if (dataSnapshot.exists()) {
                        databaseReference.child(ORDDER_REFRENCE).child(orderId)
                            .child("orderDelivery")
                            .setValue(orderStatus)
                            .addOnSuccessListener {
                                callback(true, null)
                            }
                            .addOnFailureListener { exception ->
                                callback(false, exception)
                            }
                    } else {
                        callback(false, null)
                    }
                }

                override fun onCancelled(error: DatabaseError) {

                }
            })
    }

    fun startTrackingOrder(orderId: String) {
        valueEventListener = object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                try {

                    val tempOrderStatus: OrderTracking? =
                        dataSnapshot.getValue(OrderTracking::class.java)

                    _orderDelivery.value = CustomResponse.Success(tempOrderStatus)
                } catch (e: Exception) {
                    _orderDelivery.value = CustomResponse.Error(e.message.toString())
                }

            }

            override fun onCancelled(databaseError: DatabaseError) {
                _orderDelivery.value =
                    CustomResponse.Error(databaseError.toException().message.toString())
            }
        }
        databaseReference.child(ORDDER_REFRENCE).child(orderId).child("orderDelivery")
            .addValueEventListener(valueEventListener as ValueEventListener)
    }

    fun stopTrackingOrder(orderId: String) {
        valueEventListener?.let {
            databaseReference.child(ORDDER_REFRENCE).child(orderId).child("orderDelivery")
                .removeEventListener(it)
        }
    }

    fun startObservingProceededOrders() {

        valueEventListenerOrdersList = object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                val orderList = mutableListOf<Order>()

                try {
                    for (orderSnapshot in dataSnapshot.children) {
                        val order = orderSnapshot.getValue(Order::class.java)
                        order?.let {
                            orderList.add(it)
                        }
                    }

                    _proceededOrderList.value = CustomResponse.Success(orderList)

                } catch (e: Exception) {
                    _proceededOrderList.value = CustomResponse.Error(e.message.toString())
                }


            }

            override fun onCancelled(databaseError: DatabaseError) {
                // Handle any errors that occurred during the query
            }
        }

        databaseReference.child(ORDDER_REFRENCE).orderByChild("orderDelivery/status")
            .equalTo(ORDER_PROCEED)
            .addValueEventListener(valueEventListenerOrdersList as ValueEventListener)

    }

    fun stopObservingProceededOrders() {
        valueEventListenerOrdersList?.let {
            databaseReference.child(ORDDER_REFRENCE).removeEventListener(it)
        }
    }
}


