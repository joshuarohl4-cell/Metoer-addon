import os

# ... existing code ...

def update_config(config):
    # Find all occurrences of INSTAMINE_V1 and replace them with something like "INSTAMINE"
    updated_config = config.replace("INSTAMINE_V1", "INSTAMINE")
    
    return updated_config

# Example usage:
config_data = read_file('path/to/config.ini')
updated_config_data = update_config(config_data)
write_file('path/to/updated_config.ini', updated_config_data)

# ... rest of code ...