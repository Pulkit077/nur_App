import React, {useEffect, useState} from 'react';
import {
  SafeAreaView,
  StyleSheet,
  View,
  Text,
  Button,
  FlatList,
  StatusBar,
  NativeModules,
  NativeEventEmitter,
  Platform,
  PermissionsAndroid,
  Alert,
} from 'react-native';

const {DeviceScanner} = NativeModules;

// Define interfaces for TypeScript
interface Device {
  name: string;
  address?: string;
  type?: string;
  // Add other device properties as needed
}

const App = () => {
  const [devices, setDevices] = useState<Device[]>([]);
  const [isScanning, setIsScanning] = useState(false);
  const [hasPermissions, setHasPermissions] = useState(false);

  // Request required permissions
  const requestPermissions = async () => {
    if (Platform.OS === 'android') {
      try {
        // For Android 12+ (API 31+)
        if (Platform.Version >= 31) {
          const results = await Promise.all([
            PermissionsAndroid.request(
              PermissionsAndroid.PERMISSIONS.BLUETOOTH_SCAN,
            ),
            PermissionsAndroid.request(
              PermissionsAndroid.PERMISSIONS.BLUETOOTH_CONNECT,
            ),
            PermissionsAndroid.request(
              PermissionsAndroid.PERMISSIONS.ACCESS_FINE_LOCATION,
            ),
          ]);

          const allGranted = results.every(
            result => result === PermissionsAndroid.RESULTS.GRANTED,
          );
          setHasPermissions(allGranted);
          return allGranted;
        } else {
          // For Android 11 and below
          const granted = await PermissionsAndroid.request(
            PermissionsAndroid.PERMISSIONS.ACCESS_FINE_LOCATION,
          );
          setHasPermissions(granted === PermissionsAndroid.RESULTS.GRANTED);
          return granted === PermissionsAndroid.RESULTS.GRANTED;
        }
      } catch (err) {
        console.warn(err);
        return false;
      }
    }
    return true; // iOS doesn't need runtime permissions
  };

  useEffect(() => {
    // Request permissions when component mounts
    requestPermissions();

    // Set up device scanner listeners
    const eventEmitter = new NativeEventEmitter(DeviceScanner);

    const deviceFoundListener = eventEmitter.addListener(
      'onDeviceFound',
      (device: Device) => {
        console.log('Device found:', device);
        setDevices(currentDevices => {
          // Check if device already exists in the list
          const exists = currentDevices.some(d => d.name === device.name);
          if (!exists) {
            return [...currentDevices, device];
          }
          return currentDevices;
        });
      },
    );

    const scanFinishedListener = eventEmitter.addListener(
      'onScanFinished',
      (result: {devices: Device[]}) => {
        console.log('Scan finished:', result);
        setIsScanning(false);
      },
    );

    const scanErrorListener = eventEmitter.addListener(
      'onScanError',
      (error: { error: string }) => {
        console.error('Scan error:', error);
        Alert.alert('Scan Error', error.error);
        setIsScanning(false);
      },
    );
    
    // Cleanup function
    return () => {
      deviceFoundListener.remove();
      scanFinishedListener.remove();
      scanErrorListener.remove();
      if (isScanning) {
        DeviceScanner.stopScan();
      }
        };
  }, []);

  const startScan = async () => {
    if (!hasPermissions) {
      const granted = await requestPermissions();
      if (!granted) {
        Alert.alert(
          'Permissions Required',
          'This app requires Bluetooth and Location permissions to scan for devices.',
        );
        return;
      }
    }

    try {
      setDevices([]); // Clear previous results
      setIsScanning(true);
      DeviceScanner.startScan();
    } catch (error) {
      console.error('Error starting scan:', error);
      Alert.alert('Error', 'Failed to start device scan');
      setIsScanning(false);
    }
  };

  const stopScan = () => {
    try {
      DeviceScanner.stopScan();
      setIsScanning(false);
    } catch (error) {
      console.error('Error stopping scan:', error);
    }
  };

  const renderDevice = ({item}: {item: Device}) => (
    <View style={styles.deviceItem}>
      <Text style={styles.deviceName}>{item.name || 'Unknown Device'}</Text>
      {item.address && <Text style={styles.deviceInfo}>Address: {item.address}</Text>}
      {item.type && <Text style={styles.deviceInfo}>Type: {item.type}</Text>}
    </View>
  );

  return (
    <SafeAreaView style={styles.container}>
      <StatusBar barStyle="dark-content" backgroundColor="#f5f5f5" />
      
      <View style={styles.header}>
        <Text style={styles.title}>Device Scanner</Text>
        <Button
          title={isScanning ? 'Stop Scan' : 'Start Scan'}
          onPress={isScanning ? stopScan : startScan}
          disabled={!hasPermissions && !isScanning}
        />
      </View>

      {!hasPermissions && (
        <View style={styles.permissionWarning}>
          <Text style={styles.warningText}>
            Bluetooth and Location permissions are required to scan for devices
          </Text>
          <Button title="Grant Permissions" onPress={requestPermissions} />
        </View>
      )}

      <FlatList
        data={devices}
        renderItem={renderDevice}
        keyExtractor={(item, index) => item.name || index.toString()}
        style={styles.list}
        contentContainerStyle={styles.listContent}
        ListEmptyComponent={
          <Text style={styles.emptyText}>
            {isScanning ? 'Scanning for devices...' : 'No devices found'}
          </Text>
        }
      />
    </SafeAreaView>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#f5f5f5',
  },
  header: {
    padding: 16,
    backgroundColor: '#fff',
    borderBottomWidth: 1,
    borderBottomColor: '#e0e0e0',
  },
  title: {
    fontSize: 24,
    fontWeight: 'bold',
    marginBottom: 16,
    color: '#333',
  },
  list: {
    flex: 1,
  },
  listContent: {
    padding: 16,
  },
  deviceItem: {
    backgroundColor: '#fff',
    padding: 16,
    marginBottom: 8,
    borderRadius: 8,
    elevation: 2,
    shadowColor: '#000',
    shadowOffset: {
      width: 0,
      height: 2,
    },
    shadowOpacity: 0.1,
    shadowRadius: 4,
  },
  deviceName: {
    fontSize: 16,
    fontWeight: '600',
    color: '#333',
    marginBottom: 4,
  },
  deviceInfo: {
    fontSize: 14,
    color: '#666',
    marginTop: 4,
  },
  emptyText: {
    textAlign: 'center',
    color: '#666',
    fontSize: 16,
    marginTop: 32,
  },
  permissionWarning: {
    backgroundColor: '#fff3cd',
    padding: 16,
    margin: 16,
    borderRadius: 8,
    borderWidth: 1,
    borderColor: '#ffeeba',
  },
  warningText: {
    color: '#856404',
    marginBottom: 8,
  },
});

export default App;