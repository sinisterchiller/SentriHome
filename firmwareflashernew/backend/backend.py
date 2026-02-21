from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel
import subprocess
import os
import shutil

api = FastAPI()

api.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],  # dev only
    allow_methods=["*"],
    allow_headers=["*"],
)

@api.get("/health")
def health():
    return {"health": "ok"}

class reqs(BaseModel):
    device: str
    action: str
    options: str

@api.post("/actions")
def frontendreqsfunc(data:reqs):
    devicerec = data.device
    actionrec = data.action
    optionsrec = data.options
    
    if devicerec == "wroom" and actionrec == "download":
        clone_target = "test/wroommodule/small-test-repo"
        if os.path.exists(clone_target):
            shutil.rmtree(clone_target)
        os.makedirs("test/wroommodule", exist_ok= True)
        result1 = subprocess.run(["git", "clone", "https://github.com/rtyley/small-test-repo.git"], cwd="test/wroommodule", check=True, capture_output=True, text=True)
    if devicerec == "s3" and actionrec == "download":
        clone_target = "test/s3module/small-test-repo"
        if os.path.exists(clone_target):
            shutil.rmtree(clone_target)
        os.makedirs("test/s3module", exist_ok= True)
        result2 = subprocess.run(["git", "clone", "https://github.com/rtyley/small-test-repo.git"], cwd="test/s3module", check=True, capture_output=True, text=True)

    if devicerec == "wroom" and actionrec == "status":
        result3 = subprocess.run(["ls /dev/cu.*"], shell=True, capture_output=True, text=True)
        return {"result": result3.stdout.split()}
    if devicerec == "wroom" and actionrec == "flash":
        print(devicerec, actionrec, optionsrec)

    if devicerec == "s3" and actionrec == "status":
        result3 = subprocess.run(["ls /dev/cu.*"], shell=True, capture_output=True, text=True)
        return {"result": result3.stdout.split()}
    if devicerec == "s3" and actionrec == "flash":
        print(devicerec, actionrec, optionsrec)
    return {"status": "allgood"}